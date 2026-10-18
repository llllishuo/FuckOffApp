package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A deliberately small Lua expression evaluator for user-defined protection
 * rules.
 *
 * <p>A rule is one expression, e.g.
 * {@code pkg:match("^com%.oem%.") ~= nil}. It can only add protection; the
 * baseline in {@link AppSafetyGuard} is evaluated independently and can never
 * be disabled by a rule.</p>
 *
 * <p>LuaJ is embedded without os/io/package/debug/Java libraries. Statements,
 * loops, function definitions, comments, long strings and semicolons are
 * rejected before compilation. This keeps a bad rule from hanging or crashing
 * the app; a runtime failure only disables that one rule.</p>
 */
public final class RuleEngine {

    private static final String TAG = "FuckOffRules";
    private static final int MAX_EXPRESSION_LENGTH = RuleRepository.MAX_EXPRESSION_LENGTH;

    private static final Set<String> FORBIDDEN_WORDS = new HashSet<>(Arrays.asList(
            "while", "repeat", "for", "function", "local", "return",
            "goto", "do", "end", "if", "then", "else", "elseif", "break"
    ));

    private static final String[] REMOVED_GLOBALS = new String[]{
            "load", "loadfile", "dofile", "require", "package",
            "io", "os", "debug", "luajava", "module", "print",
            "getfenv", "setfenv"
    };

    private static final String[] REMOVED_STRING_FUNCTIONS = new String[]{
            "rep", "char", "format", "dump"
    };

    private RuleEngine() {
    }

    public static ValidationResult validate(String expression) {
        String structuralError = checkExpression(expression);
        if (structuralError != null) {
            return ValidationResult.error(structuralError);
        }
        try {
            Globals globals = newGlobals();
            LuaValue chunk = globals.load(
                    "return (" + expression.trim() + ")", "rule");
            setEnvironment(globals, "com.example.rule.test");
            chunk.call();
            return ValidationResult.ok();
        } catch (LuaError e) {
            return ValidationResult.error("Lua 错误：" + cleanError(e.getMessage()));
        } catch (Throwable t) {
            return ValidationResult.error("规则无法运行：" + cleanError(t.getMessage()));
        }
    }

    /**
     * Builds a per-role-snapshot evaluator. Any failure to initialize the Lua
     * engine only means custom rules are ignored; the baseline still runs.
     */
    public static Evaluator create(Context context) {
        List<Evaluator.CompiledRule> compiled = new ArrayList<>();
        try {
            List<RuleRepository.Rule> rules =
                    new RuleRepository(context).getEnabled();
            for (RuleRepository.Rule rule : rules) {
                if (checkExpression(rule.expression) != null) {
                    continue;
                }
                try {
                    Globals globals = newGlobals();
                    LuaValue chunk = globals.load(
                            "return (" + rule.expression.trim() + ")",
                            "rule:" + rule.id);
                    compiled.add(new Evaluator.CompiledRule(rule, globals, chunk));
                } catch (Throwable t) {
                    Log.w(TAG, "Skipping invalid rule " + rule.id, t);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Lua rules unavailable", t);
            compiled.clear();
        }
        return new Evaluator(compiled);
    }

    private static Globals newGlobals() {
        Globals globals = new Globals();
        globals.load(new BaseLib());
        globals.load(new PackageLib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new JseMathLib());
        LuaC.install(globals);

        for (String name : REMOVED_GLOBALS) {
            globals.set(name, LuaValue.NIL);
        }
        LuaValue stringLib = globals.get("string");
        if (!stringLib.isnil()) {
            for (String name : REMOVED_STRING_FUNCTIONS) {
                stringLib.set(name, LuaValue.NIL);
            }
        }
        globals.set("brand", safe(Build.BRAND));
        globals.set("manufacturer", safe(Build.MANUFACTURER));
        globals.set("model", safe(Build.MODEL));
        globals.set("device", safe(Build.DEVICE));
        globals.set("sdk", Build.VERSION.SDK_INT);
        return globals;
    }

    private static void setEnvironment(Globals globals, String packageName) {
        globals.set("pkg", safe(packageName));
        globals.set("sdk", Build.VERSION.SDK_INT);
        globals.set("brand", safe(Build.BRAND));
        globals.set("manufacturer", safe(Build.MANUFACTURER));
        globals.set("model", safe(Build.MODEL));
        globals.set("device", safe(Build.DEVICE));
    }

    private static String checkExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return "表达式不能为空";
        }
        String expr = expression.trim();
        if (expr.length() > MAX_EXPRESSION_LENGTH) {
            return "表达式过长（最多 " + MAX_EXPRESSION_LENGTH + " 个字符）";
        }

        char quote = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (c == '-' && i + 1 < expr.length() && expr.charAt(i + 1) == '-') {
                return "不支持注释";
            }
            if (c == '[' && i + 1 < expr.length()
                    && (expr.charAt(i + 1) == '[' || expr.charAt(i + 1) == '=')) {
                return "不支持长字符串";
            }
            if (c == ';') {
                return "不支持分号或多条语句";
            }
            if (isIdentifierStart(c)) {
                int start = i;
                i++;
                while (i < expr.length() && isIdentifierPart(expr.charAt(i))) {
                    i++;
                }
                String word = expr.substring(start, i);
                if (FORBIDDEN_WORDS.contains(word)) {
                    return "不支持语句关键字：" + word;
                }
                i--;
            }
        }
        if (quote != 0) {
            return "字符串未闭合";
        }
        return null;
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String cleanError(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "未知错误";
        }
        String clean = message.replace('\n', ' ').trim();
        return clean.length() > 180 ? clean.substring(0, 180) : clean;
    }

    public static final class ValidationResult {
        public final boolean valid;
        public final String error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }

    public static final class Evaluator implements AppSafetyGuard.CustomProtection {

        private final List<CompiledRule> rules;

        private Evaluator(List<CompiledRule> rules) {
            this.rules = rules;
        }

        public boolean isProtected(String packageName) {
            if (rules.isEmpty() || packageName == null) {
                return false;
            }
            for (CompiledRule rule : rules) {
                try {
                    rule.globals.set("pkg", safe(packageName));
                    if (rule.chunk.call().toboolean()) {
                        return true;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Rule evaluation failed: " + rule.rule.id, t);
                }
            }
            return false;
        }

        private static final class CompiledRule {
            final RuleRepository.Rule rule;
            final Globals globals;
            final LuaValue chunk;

            CompiledRule(RuleRepository.Rule rule, Globals globals, LuaValue chunk) {
                this.rule = rule;
                this.globals = globals;
                this.chunk = chunk;
            }
        }
    }

}
