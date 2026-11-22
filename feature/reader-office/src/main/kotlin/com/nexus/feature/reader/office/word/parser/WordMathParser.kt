package com.nexus.feature.reader.office.word.parser

import org.w3c.dom.Node

object WordMathParser {

    /**
     * Translates Symbol font ASCII characters into proper Greek and mathematical Unicode symbols.
     */
    fun decodeSymbolFont(text: String): String {
        return text.map { ch ->
            when (ch) {
                'a' -> 'α'; 'b' -> 'β'; 'g' -> 'γ'; 'd' -> 'δ'; 'e' -> 'ε'; 'z' -> 'ζ'
                'h' -> 'η'; 'q' -> 'θ'; 'i' -> 'ι'; 'k' -> 'κ'; 'l' -> 'λ'; 'm' -> 'μ'
                'n' -> 'ν'; 'x' -> 'ξ'; 'o' -> 'ο'; 'p' -> 'π'; 'r' -> 'ρ'; 's' -> 'σ'
                't' -> 'τ'; 'u' -> 'υ'; 'f' -> 'φ'; 'c' -> 'χ'; 'y' -> 'ψ'; 'w' -> 'ω'
                'A' -> 'Α'; 'B' -> 'Β'; 'G' -> 'Γ'; 'D' -> 'Δ'; 'E' -> 'Ε'; 'Z' -> 'Ζ'
                'H' -> 'Η'; 'Q' -> 'Θ'; 'I' -> 'Ι'; 'K' -> 'Κ'; 'L' -> 'Λ'; 'M' -> 'Μ'
                'N' -> 'Ν'; 'X' -> 'Ξ'; 'O' -> 'Ο'; 'P' -> 'Π'; 'R' -> 'Ρ'; 'S' -> 'Σ'
                'T' -> 'Τ'; 'U' -> 'Υ'; 'F' -> 'Φ'; 'C' -> 'Χ'; 'Y' -> 'Ψ'; 'W' -> 'Ω'
                '\u00B1' -> '±'; '\u00B4' -> '×'; '\u00B8' -> '÷'; '\u00A5' -> '∞'
                '\u00B9' -> '≠'; '\u00A3' -> '≤'; '\u00B3' -> '≥'; '\u00D6' -> '√'
                '\u00F2' -> '∫'; '\u00CE' -> '∈'; '\u00CF' -> '∉'; '\u00CC' -> '⊂'
                '\u00C9' -> '⊃'; '\u00C8' -> '⊆'; '\u00CA' -> '⊇'; '\u00C0' -> 'ℵ'
                '\u00A9' -> '∇'; '\u00B6' -> '∂'; '\u00DC' -> '¬'; '\u00DE' -> '⇒'
                '\u00DB' -> '⇔'; '\u00D8' -> '→'; '\u00AC' -> '←'; '\u00AD' -> '↑'
                '\u00AF' -> '↓'; '\u00AB' -> '↔'; '\u00D1' -> '≡'; '\u00BB' -> '≈'
                else -> ch
            }
        }.joinToString("")
    }

    /**
     * Decodes TeX/LaTeX-style symbol macros and common mathematical notation in plain text.
     */
    fun decodeMathSymbols(text: String): String {
        var result = text
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\delta", "δ")
            .replace("\\epsilon", "ε")
            .replace("\\zeta", "ζ")
            .replace("\\eta", "η")
            .replace("\\theta", "θ")
            .replace("\\iota", "ι")
            .replace("\\kappa", "κ")
            .replace("\\lambda", "λ")
            .replace("\\mu", "μ")
            .replace("\\nu", "ν")
            .replace("\\xi", "ξ")
            .replace("\\pi", "π")
            .replace("\\rho", "ρ")
            .replace("\\sigma", "σ")
            .replace("\\tau", "τ")
            .replace("\\upsilon", "υ")
            .replace("\\phi", "φ")
            .replace("\\chi", "χ")
            .replace("\\psi", "ψ")
            .replace("\\omega", "ω")
            .replace("\\Gamma", "Γ")
            .replace("\\Delta", "Δ")
            .replace("\\Theta", "Θ")
            .replace("\\Lambda", "Λ")
            .replace("\\Xi", "Ξ")
            .replace("\\Pi", "Π")
            .replace("\\Sigma", "Σ")
            .replace("\\Phi", "Φ")
            .replace("\\Psi", "Ψ")
            .replace("\\Omega", "Ω")
            .replace("\\pm", "±")
            .replace("\\mp", "∓")
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\cdot", "·")
            .replace("\\bullet", "•")
            .replace("\\leq", "≤")
            .replace("\\geq", "≥")
            .replace("\\neq", "≠")
            .replace("\\approx", "≈")
            .replace("\\equiv", "≡")
            .replace("\\infty", "∞")
            .replace("\\forall", "∀")
            .replace("\\exists", "∃")
            .replace("\\in", "∈")
            .replace("\\notin", "∉")
            .replace("\\subset", "⊂")
            .replace("\\supset", "⊃")
            .replace("\\subseteq", "⊆")
            .replace("\\supseteq", "⊇")
            .replace("\\cup", "∪")
            .replace("\\cap", "∩")
            .replace("\\int", "∫")
            .replace("\\sum", "∑")
            .replace("\\prod", "∏")
            .replace("\\sqrt", "√")
            .replace("\\partial", "∂")
            .replace("\\nabla", "∇")
            .replace("\\to", "→")
            .replace("\\rightarrow", "→")
            .replace("\\leftarrow", "←")
            .replace("\\Rightarrow", "⇒")
            .replace("\\Leftarrow", "⇐")
            .replace("\\Leftrightarrow", "⇔")
            .replace("\\degree", "°")

        return result
    }

    /**
     * Checks if a DOM node is an Office Math (OMML) node.
     */
    fun isMathNode(node: Node): Boolean {
        val name = node.nodeName.lowercase()
        val local = (node.localName ?: name.substringAfterLast(':')).lowercase()
        return local == "omath" || local == "omathpara" || name.contains("omath")
    }

    /**
     * Parses an OMML Node recursively and appends structured HTML into [sb].
     */
    fun parseOMML(node: Node, sb: StringBuilder) {
        val localName = (node.localName ?: node.nodeName.substringAfterLast(':')).lowercase()
        when (localName) {
            "omathpara" -> {
                sb.append("<div class=\"math-block\"><span class=\"math-formula math-display\">")
                for (i in 0 until node.childNodes.length) {
                    parseOMML(node.childNodes.item(i), sb)
                }
                sb.append("</span></div>\n")
            }
            "omath" -> {
                sb.append("<span class=\"math-formula math-inline\">")
                for (i in 0 until node.childNodes.length) {
                    parseOMML(node.childNodes.item(i), sb)
                }
                sb.append("</span>")
            }
            "f" -> { // Fraction
                var numHtml = ""
                var denHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "num") {
                        val nSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), nSb)
                        numHtml = nSb.toString()
                    } else if (cName == "den") {
                        val dSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), dSb)
                        denHtml = dSb.toString()
                    }
                }
                sb.append("<span class=\"math-fraction\"><span class=\"math-num\">$numHtml</span><span class=\"math-den\">$denHtml</span></span>")
            }
            "rad" -> { // Radical / Root
                var degHtml = ""
                var radHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "deg") {
                        val dSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), dSb)
                        degHtml = dSb.toString().trim()
                    } else if (cName == "e") {
                        val rSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), rSb)
                        radHtml = rSb.toString()
                    }
                }
                sb.append("<span class=\"math-radical\">")
                if (degHtml.isNotBlank()) {
                    sb.append("<sup class=\"math-deg\">$degHtml</sup>")
                }
                sb.append("<span class=\"math-sqrt-sym\">√</span><span class=\"math-radicand\">$radHtml</span></span>")
            }
            "ssup" -> { // Superscript
                var baseHtml = ""
                var supHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "e") {
                        val bSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), bSb)
                        baseHtml = bSb.toString()
                    } else if (cName == "sup") {
                        val sSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), sSb)
                        supHtml = sSb.toString()
                    }
                }
                sb.append("<span class=\"math-base\">$baseHtml</span><sup class=\"math-sup\">$supHtml</sup>")
            }
            "ssub" -> { // Subscript
                var baseHtml = ""
                var subHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "e") {
                        val bSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), bSb)
                        baseHtml = bSb.toString()
                    } else if (cName == "sub") {
                        val sSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), sSb)
                        subHtml = sSb.toString()
                    }
                }
                sb.append("<span class=\"math-base\">$baseHtml</span><sub class=\"math-sub\">$subHtml</sub>")
            }
            "ssubsup" -> { // Sub-Superscript
                var baseHtml = ""
                var subHtml = ""
                var supHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "e") {
                        val bSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), bSb)
                        baseHtml = bSb.toString()
                    } else if (cName == "sub") {
                        val sSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), sSb)
                        subHtml = sSb.toString()
                    } else if (cName == "sup") {
                        val spSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), spSb)
                        supHtml = spSb.toString()
                    }
                }
                sb.append("<span class=\"math-base\">$baseHtml</span><sub class=\"math-sub\">$subHtml</sub><sup class=\"math-sup\">$supHtml</sup>")
            }
            "d" -> { // Delimiter / Parentheses
                var begChr = "("
                var endChr = ")"
                var exprHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "dpr") {
                        for (j in 0 until child.childNodes.length) {
                            val prop = child.childNodes.item(j)
                            val pName = (prop.localName ?: prop.nodeName.substringAfterLast(':')).lowercase()
                            val `val` = prop.attributes?.getNamedItem("m:val")?.nodeValue
                                ?: prop.attributes?.getNamedItem("val")?.nodeValue
                            if (pName == "begchr" && `val` != null) begChr = `val`
                            if (pName == "endchr" && `val` != null) endChr = `val`
                        }
                    } else if (cName == "e") {
                        val eSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), eSb)
                        exprHtml += eSb.toString()
                    }
                }
                sb.append("<span class=\"math-delim\">$begChr</span>$exprHtml<span class=\"math-delim\">$endChr</span>")
            }
            "nary" -> { // N-ary (Sum, Integral, Product)
                var opChar = "∑"
                var subHtml = ""
                var supHtml = ""
                var exprHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "narypr") {
                        for (j in 0 until child.childNodes.length) {
                            val prop = child.childNodes.item(j)
                            val pName = (prop.localName ?: prop.nodeName.substringAfterLast(':')).lowercase()
                            if (pName == "chr") {
                                val v = prop.attributes?.getNamedItem("m:val")?.nodeValue
                                    ?: prop.attributes?.getNamedItem("val")?.nodeValue
                                if (v != null) opChar = v
                            }
                        }
                    } else if (cName == "sub") {
                        val sSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), sSb)
                        subHtml = sSb.toString()
                    } else if (cName == "sup") {
                        val spSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), spSb)
                        supHtml = spSb.toString()
                    } else if (cName == "e") {
                        val eSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), eSb)
                        exprHtml = eSb.toString()
                    }
                }
                sb.append("<span class=\"math-nary\"><span class=\"math-nary-op\">$opChar</span><span class=\"math-nary-limits\"><sup class=\"math-limit-sup\">$supHtml</sup><sub class=\"math-limit-sub\">$subHtml</sub></span><span class=\"math-nary-expr\">$exprHtml</span></span>")
            }
            "func" -> { // Function (sin, cos, lim, etc.)
                var fName = ""
                var argHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "fname") {
                        val fnSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), fnSb)
                        fName = fnSb.toString()
                    } else if (cName == "e") {
                        val aSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), aSb)
                        argHtml = aSb.toString()
                    }
                }
                sb.append("<span class=\"math-func\"><span class=\"math-func-name\">$fName</span><span class=\"math-func-arg\">$argHtml</span></span>")
            }
            "limlow" -> { // Limit lower
                var eHtml = ""
                var limHtml = ""
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "e") {
                        val eSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), eSb)
                        eHtml = eSb.toString()
                    } else if (cName == "lim") {
                        val lSb = StringBuilder()
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), lSb)
                        limHtml = lSb.toString()
                    }
                }
                sb.append("<span class=\"math-limit-block\"><span class=\"math-limit-base\">$eHtml</span><sub class=\"math-limit-sub\">$limHtml</sub></span>")
            }
            "bar" -> { // Overline / bar
                val bSb = StringBuilder()
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    if ((child.localName ?: child.nodeName.substringAfterLast(':')).lowercase() == "e") {
                        for (j in 0 until child.childNodes.length) parseOMML(child.childNodes.item(j), bSb)
                    }
                }
                sb.append("<span class=\"math-bar\">$bSb</span>")
            }
            "r" -> { // Math run
                val rSb = StringBuilder()
                for (i in 0 until node.childNodes.length) {
                    val child = node.childNodes.item(i)
                    val cName = (child.localName ?: child.nodeName.substringAfterLast(':')).lowercase()
                    if (cName == "t") {
                        val t = child.textContent ?: ""
                        rSb.append(escapeHtml(decodeMathSymbols(t)))
                    }
                }
                sb.append("<span class=\"math-text\">$rSb</span>")
            }
            "t" -> {
                sb.append("<span class=\"math-text\">${escapeHtml(decodeMathSymbols(node.textContent ?: ""))}</span>")
            }
            else -> {
                for (i in 0 until node.childNodes.length) {
                    parseOMML(node.childNodes.item(i), sb)
                }
            }
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
