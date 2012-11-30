package dk.itu.formhelper.builders
import dk.itu.formhelper.FormHelper._

object HtmlBuilder {
  private val indent = "  "

  // Build Html for a Form
  def plainHtml(form: Form): String = htmlForm(form, false)
  // Build Html for a Form with added validation method calls
  def htmlWithValidation(form: Form): String = htmlForm(form, true)

  // Build Html for a Field
  def htmlField(f: Field, validate: Boolean): String =
    innerFieldHtml("<input type=\"" + f.inputType + "\" name=\"" + f.fname + valueHelper(f.value) + "\"" + validationHelper(f, validate), f.styles, f.rules)

  // Adds validation method to a Field
  private def validationHelper(field: Field, validate: Boolean): String = field match {
    case Submit(_, _, _, _) => ""
    case _ => if (validate) " onblur=\"validate" + field.fname + "()\"" else ""
  }

  // Value string helper
  private def valueHelper(value: String): String = if (value.isEmpty()) "" else "\"" + " value=\"" + value

  // Create Html from a Form type
  private def htmlForm(form: Form, validate: Boolean): String = {

    // Places <br> after each field except the last field
    def brHelper(field: Field): String = if (field.styles.contains(SameLine)) "" else "<br>"

    def methodHelper(method: Method): String = method match {
      case Get => "method=\"get\""
      case Post => "method=\"post\""
    }

    // Build HTML for form, add newline after each field except the last
    def newLineHelper(fields: List[Field], html: String): String = fields match {
      case Nil => html + "</form>"
      case f :: Nil => newLineHelper(Nil, html + indent + htmlField(f, validate) + "\n")
      case f :: fs => newLineHelper(fs, html + indent + htmlField(f, validate) + brHelper(f) + "\n")
    }

    val validation = if (validate) " onsubmit=\"return validateForm()\"" else ""
    newLineHelper(form.fields.toList, "<form name=\"" + form.name + "\" " + methodHelper(form.method) + " action=\"" + form.action + "\"" + validation + ">\n")
  }

  // Build HTML for requirements
  private def reqHelper(rules: List[Rule], start: String, end: String): String = {
    def valueHelper(rules2: List[Rule], vals: (Double, Double, Double, ValidationType)): ValueRule = rules2 match {
      case ValueRule(min1, max1, equals1, valType) :: xs =>
        val min2 = min1 max vals._1
        val max2 = max1 max vals._2
        val equals2 = equals1 max vals._3
        valueHelper(xs, (min2, max2, equals2, valType))
      case _ :: xs => valueHelper(xs, vals)
      case Nil => ValueRule(vals._1, vals._2, vals._3, vals._4)
    }

    // Helper for the different value types. Returns a tuple with the type and ending as strings. And a function to format the value.
    def valTypeHelper(value: ValueRule) = value.valType match {
      case LengthValidation => ("Length", " characters", (d: Double) => d.toInt)
      case IntValidation => ("Integer", "", (d: Double) => d.toInt)
      case FloatValidation => ("Float", "", (d: Double) => d)
    }

    def valueString(value: ValueRule): String = {
      val (valueTypeString, l_end, numberFormat) = valTypeHelper(value)
      val l_start = start + valueTypeString + " must be "
      value match {
        case ValueRule(min, -1, -1, valType) => l_start + "greater-than " + numberFormat(min) + l_end
        case ValueRule(-1, max, -1, valType) => l_start + "less-than " + numberFormat(max) + l_end
        case ValueRule(min, max, -1, valType) => l_start + "greater-than " + numberFormat(min) + " and less-than " + numberFormat(max) + l_end
        case ValueRule(min, max, equals, valType) =>
          if (equals == min)
            if (max == -1) l_start + "greater-than or equal to " + numberFormat(equals) + l_end
            else l_start + "greater-than or equal to " + numberFormat(equals) + " and less-than " + numberFormat(max) + l_end
          else if (equals == max)
            if (min == -1) l_start + "less-than or equal to " + numberFormat(equals) + l_end
            else l_start + "greater-than " + numberFormat(min) + " and less-than or equal to " + numberFormat(equals) + l_end
          else l_start + numberFormat(equals) + l_end
      }
    }

    def isValue(r: Rule) = r match {
      case ValueRule(_, _, _, _) => true
      case _ => false
    }

    rules match {
      case Required :: xs => start + "Field is required" + end + reqHelper(xs, start, end)
      case ValueRule(min, max, equals, valType) :: xs => valueString(valueHelper(xs, (min, max, equals, valType))) + reqHelper(xs.filter(r => (!isValue(r))), start, end)
      case Email :: xs => start + "Must be a valid email" + end + reqHelper(xs, start, end)
      case IP :: xs => start + "Must be a valid IP" + end + reqHelper(xs, start, end)
      case Integer :: xs => start + "Must be an integer" + end + reqHelper(xs, start, end)
      case Float :: xs => start + "Must be a float" + end + reqHelper(xs, start, end)
      case Alpha :: xs => start + "Must be alphanumeric" + end + reqHelper(xs, start, end)
      case Regex(regex, bool) :: xs =>
        val not = if (bool) "" else "not"
        start + "Must " + not + " match this regex: " + regex + end + reqHelper(xs, start, end)
      case _ => ""
    }
  }

  // Build HTML for an rule related type
  private def ruleHelper(errMsg: String, styles: List[Style], rules: List[Rule]): String = {
    val fieldInfoStart = "\n" + indent + indent + "<field_message class=\"info\">"
    val fieldErrorStart = "\n" + indent + indent + "<field_message class=\"error\">"
    val fieldEnd = "</field>"

    def containsError(ss: List[Style]): Boolean = ss match {
      case Error(msg) :: xs => true
      case _ :: xs => containsError(xs)
      case Nil => false
    }

    if (styles.contains(ShowRequirements) && containsError(styles))
      if (errMsg.isEmpty()) "" else fieldInfoStart + errMsg + fieldEnd
    else if (styles.contains(ShowRequirements) && !containsError(styles))
      reqHelper(rules, fieldInfoStart, fieldEnd)
    else if (styles.contains(ShowErrors) && containsError(styles))
      if (errMsg.isEmpty()) "" else fieldErrorStart + errMsg + fieldEnd
    else if (styles.contains(ShowErrors) && !containsError(styles))
      reqHelper(rules, fieldErrorStart, fieldEnd)
    else ""
  }

  // Helps build the html for a field
  private def innerFieldHtml(html: String, styles: List[Style], rules: List[Rule]): String = {
    // Helper method
    def innerFieldHelper(inner_html: String, inner_styles: List[Style], inner_rules: List[Rule]): String = inner_styles match {
      case Label(label, placement) :: xs => placement match {
        case Before => innerFieldHelper(label + inner_html, xs, inner_rules)
        case After => innerFieldHelper(inner_html, xs, inner_rules) + label
        // HTML 5 feature
        case Inside => innerFieldHelper(inner_html + " placeholder=\"" + label + "\"", xs, inner_rules)
      }
      case SameLine :: xs => innerFieldHelper(inner_html, xs, inner_rules)

      // Radio button checked
      case Checked :: xs => innerFieldHelper(inner_html + " checked", xs, inner_rules)

      // Error related cases
      case Error(msg) :: xs => innerFieldHelper(inner_html, xs, inner_rules) + ruleHelper(msg, filterStyles(styles), rules)
      case ShowRequirements :: xs => innerFieldHelper(inner_html, xs, inner_rules) + ruleHelper("", filterStyles(styles), rules)
      case ShowErrors :: xs => innerFieldHelper(inner_html, xs, inner_rules) + ruleHelper("", filterStyles(styles), rules)
      case _ => inner_html + ">"
    }

    // Remove requirements if the field has errors in validation
    def filterStyles(sl: List[Style]): List[Style] = {
      if (sl.contains(ShowErrors)) sl.filter(s => s != ShowRequirements).distinct
      else sl.distinct
    }

    // Start the helper method
    innerFieldHelper(html, filterStyles(styles), rules)
  }
}