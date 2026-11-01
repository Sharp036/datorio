package com.datorio.query.compiler

import com.datorio.model.DataSourceType
import org.slf4j.LoggerFactory

/**
 * Resolves named parameters (:paramName) in SQL by substituting them with escaped literals.
 *
 * In a production system you'd ideally use JDBC PreparedStatement with positional
 * parameters. This resolver is a middle ground that works with both PG and CH
 * drivers without requiring driver-specific named-parameter support.
 *
 * Rules:
 *  - a placeholder is ":name" outside string literals, quoted identifiers and comments;
 *    "::type" (a PostgreSQL cast) is not a placeholder;
 *  - a list value (a multi-select) becomes "('a', 'b')", or "'a', 'b'" when the placeholder
 *    already stands inside parentheses, so both "IN :p" and "IN (:p)" work; an empty list
 *    counts as a missing value;
 *  - a missing or blank value removes a simple condition "AND col op :p" (also "WHERE col op :p"
 *    and a qualified "t.col"), which becomes "1=1"; a placeholder left in a more complex
 *    expression becomes ''.
 *
 * Security: all values are escaped through the dialect's literal formatting.
 */
class ParameterResolver(dialectType: DataSourceType) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dialect = SqlDialectFactory.create(dialectType)

    /**
     * Resolve named parameters in SQL.
     *
     * @param sql SQL with :paramName placeholders
     * @param parameters map of parameter name -> value
     * @return SQL with parameters replaced by escaped literals
     */
    fun resolve(sql: String, parameters: Map<String, Any?>): String {
        val found = extractParameterNames(sql)
        if (found.isEmpty()) return sql

        var resolved = sql
        val missing = found.filter { name ->
            val value = parameters[name]
            !parameters.containsKey(name) || value == null || (value is String && value.isBlank()) ||
                (value is Collection<*> && value.isEmpty())
        }.toSet()
        for (name in missing) {
            resolved = removeSimpleConditions(resolved, name)
        }

        resolved = replacePlaceholders(resolved) { name, inParentheses ->
            when {
                name in missing -> "''"
                else -> formatParameterValue(parameters[name], inParentheses)
            }
        }

        log.debug("Resolved {} parameters in SQL", found.size)
        return resolved
    }

    /**
     * Extract all named parameter names from a SQL string, in order of first appearance.
     */
    fun extractParameterNames(sql: String): List<String> {
        val names = LinkedHashSet<String>()
        replacePlaceholders(sql) { name, _ -> names.add(name); ":$name" }
        return names.toList()
    }

    /**
     * Walks the SQL once and hands every placeholder outside literals, quoted identifiers and
     * comments to [replacement], together with whether it stands directly inside parentheses.
     */
    private fun replacePlaceholders(sql: String, replacement: (String, Boolean) -> String): String {
        val out = StringBuilder(sql.length + 32)
        var i = 0
        var quote: Char? = null
        while (i < sql.length) {
            val c = sql[i]
            when {
                quote != null -> {
                    out.append(c)
                    if (c == '\\' && quote == '\'' && i + 1 < sql.length) { out.append(sql[i + 1]); i++ }
                    else if (c == quote) quote = null
                    i++
                }
                c == '\'' || c == '"' || c == '`' -> { quote = c; out.append(c); i++ }
                c == '-' && sql.startsWith("--", i) -> {
                    val end = sql.indexOf('\n', i).let { if (it < 0) sql.length else it }
                    out.append(sql, i, end); i = end
                }
                c == '/' && sql.startsWith("/*", i) -> {
                    val end = sql.indexOf("*/", i + 2).let { if (it < 0) sql.length else it + 2 }
                    out.append(sql, i, end); i = end
                }
                c == ':' -> {
                    val prev = if (i > 0) sql[i - 1] else ' '
                    val next = if (i + 1 < sql.length) sql[i + 1] else ' '
                    if (prev == ':' || next == ':' || prev.isLetterOrDigit() || prev == '_' || !(next.isLetter() || next == '_')) {
                        out.append(c); i++
                    } else {
                        var j = i + 1
                        while (j < sql.length && (sql[j].isLetterOrDigit() || sql[j] == '_')) j++
                        val name = sql.substring(i + 1, j)
                        out.append(replacement(name, insideParentheses(sql, i, j)))
                        i = j
                    }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** True when the placeholder at [start, end) is wrapped as "( :p )" with nothing else inside. */
    private fun insideParentheses(sql: String, start: Int, end: Int): Boolean {
        var before = start - 1
        while (before >= 0 && sql[before].isWhitespace()) before--
        var after = end
        while (after < sql.length && sql[after].isWhitespace()) after++
        return before >= 0 && sql[before] == '(' && after < sql.length && sql[after] == ')'
    }

    /**
     * Remove a simple condition "AND column op :param" (or the first condition after WHERE)
     * by replacing it with "1=1". The column may be qualified ("t.col") or quoted.
     * Conditions in parentheses or other expressions are left for the '' substitution.
     */
    private fun removeSimpleConditions(sql: String, paramName: String): String {
        val column = """(?:[\w"`]+\.)*[\w"`]+"""
        val operator = """(?:=|!=|<>|<=|>=|<|>|(?i:NOT\s+)?(?i:I?LIKE)|(?i:NOT\s+)?(?i:IN))"""
        val regex = Regex("""(?i)\b(AND|WHERE)\s+$column\s*$operator\s*\(?\s*:${Regex.escape(paramName)}\b(?:\s*\))?""")
        return regex.replace(sql) { m -> "${m.groupValues[1]} 1=1" }
    }

    private fun formatParameterValue(value: Any?, inParentheses: Boolean = false): String {
        return when (value) {
            null -> "NULL"
            is String -> dialect.formatStringLiteral(value)
            is Boolean -> dialect.formatBooleanLiteral(value)
            is Number -> value.toString()
            is Collection<*> -> {
                val items = if (value.isEmpty()) "NULL" else value.joinToString(", ") { formatParameterValue(it) }
                if (inParentheses) items else "($items)"
            }
            is Array<*> -> formatParameterValue(value.toList(), inParentheses)
            else -> dialect.formatStringLiteral(value.toString())
        }
    }
}
