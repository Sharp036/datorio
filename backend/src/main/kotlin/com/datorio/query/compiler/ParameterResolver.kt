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
 *  - a list value (sent by an API client; the parameter panel sends a multi-select as the
 *    string "a,b") becomes "('a', 'b')", or "'a', 'b'" when the placeholder already stands
 *    inside parentheses or brackets; an empty list counts as a missing value;
 *  - a missing or blank value turns a simple condition "AND col op :p" into "AND 1=1"; any other
 *    placeholder becomes '' (so "WHERE col = :p" matches nothing until a value is chosen, which
 *    hidden drill-down widgets rely on).
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

    /** True when the placeholder at [start, end) is wrapped as "( :p )" or "[ :p ]" with nothing else inside. */
    private fun insideParentheses(sql: String, start: Int, end: Int): Boolean {
        var before = start - 1
        while (before >= 0 && sql[before].isWhitespace()) before--
        var after = end
        while (after < sql.length && sql[after].isWhitespace()) after++
        if (before < 0 || after >= sql.length) return false
        return (sql[before] == '(' && sql[after] == ')') || (sql[before] == '[' && sql[after] == ']')
    }

    /**
     * Turn a simple condition "AND column op :param" into "AND 1=1".
     * Conditions after WHERE, in parentheses or other expressions are left for the '' substitution.
     */
    private fun removeSimpleConditions(sql: String, paramName: String): String {
        return sql.replace(
            Regex("""(?i)\bAND\s+\w+\s*[=!<>]+\s*:${Regex.escape(paramName)}\b"""),
            "AND 1=1"
        )
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
