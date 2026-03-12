package net.rptools.util;

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.LogHelper;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.helper.ext.AssignHelper;
import com.github.jknack.handlebars.helper.ext.IncludeHelper;
import com.github.jknack.handlebars.helper.ext.NumberHelper;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.apache.commons.lang3.Validate.notNull;

public class HandlebarsHelpers {
    static Handlebars registerHelpers(Handlebars handlebars) {
        StringHelpers.register(handlebars);
        NumberHelper.register(handlebars);
        Arrays.stream(ConditionalHelpers.values()).forEach(h -> handlebars.registerHelper(h.name(), h));
        handlebars.registerHelper("json", Jackson2Helper.INSTANCE);
        handlebars.registerHelper(AssignHelper.NAME, AssignHelper.INSTANCE);
        handlebars.registerHelper(IncludeHelper.NAME, IncludeHelper.INSTANCE);

        MathsHelpers.register(handlebars);
        StringComparison.register(handlebars);
        handlebars.registerHelper(MarkdownHelper.NAME, MarkdownHelper.INSTANCE);
        handlebars.registerHelper(Base64EncodeHelper.NAME, Base64EncodeHelper.INSTANCE);
        handlebars.registerHelper(HBLogger.NAME, HBLogger.INSTANCE);

        return handlebars;
    }

    static class Base64EncodeHelper implements Helper<Object> {
        /**
         * A singleton instance of this helper.
         */
        public static final Helper<Object> INSTANCE = new Base64EncodeHelper();

        /**
         * The helper's name.
         */
        public static final String NAME = "base64Encode";

        /**
         * Turns the textual form of the value into a base64-encoded string. For example:
         *
         * <pre>
         * &lt;script type="application/json;base64" id="jsonProperty"&gt;
         *   {{ base64Encode properties[0].value }}
         * &lt;/script&gt;
         * &lt;script type="application/javascript"&gt;
         * const jsonProperty = JSON.parse(atob(document.getElementById("jsonProperty").innerText));
         * &lt;/script&gt;
         * </pre>
         */

        @Override
        public Object apply(final Object context, final Options options) {
            byte[] message = context.toString().getBytes(StandardCharsets.UTF_8);
            return new Handlebars.SafeString(Base64.getUrlEncoder().encodeToString(message));
        }
    }

    public enum MathsHelpers implements Helper<Object> {
        add {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options);
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.add(numbers.removeLast());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        subtract {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options);
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.subtract(numbers.removeLast());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        multiply {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options).reversed();
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.multiply(numbers.removeLast());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        divide {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options).reversed();
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.divide(numbers.removeLast(), RoundingMode.HALF_EVEN);
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        max {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options);
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.max(numbers.removeLast());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        min {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options);
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.min(numbers.removeLast());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        mod {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options);
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.remainder(numbers.removeLast());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        div {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options);
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.divideToIntegralValue(numbers.removeLast());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        pow {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    List<BigDecimal> numbers = numbers(a, options);
                    BigDecimal result = numbers.removeLast();
                    while (!numbers.isEmpty()) {
                        result = result.pow(numbers.removeLast().intValue());
                    }
                    return String.valueOf(result);
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        abs {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    return new BigDecimal(a.toString()).abs().toString();
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },
        sqrt {
            @Override
            public Object apply(final Object a, final Options options) {
                try {
                    return new BigDecimal(a.toString()).sqrt(MathContext.DECIMAL32).toString();
                } catch (Exception ignored) {
                    return "NaN";
                }
            }
        },

        tau {
            @Override
            public Object apply(final Object a, final Options options) {
                return String.valueOf(Math.TAU);
            }
        },
        pi {
            @Override
            public Object apply(final Object a, final Options options) {
                return String.valueOf(Math.PI);
            }
        },
        length {
            @Override
            public Object apply(final Object a, final Options options) {
                return a.toString().length();
            }
        }

        ;

        List<BigDecimal> numbers(Object a, final Options options) {
            int count = options.params.length + 1;
            List<BigDecimal> values = new ArrayList<>();
            try {
                values.add(new BigDecimal(a.toString()));
                for (int i = 0; i < count; i++) {
                    values.add(new BigDecimal((double) options.param(i)));
                }

            } catch (NumberFormatException ignored) {
            }
            return values;
        }
        /**
         * Register the helper in a handlebars instance.
         *
         * @param handlebars A handlebars object. Required.
         */
        public void registerHelper(final Handlebars handlebars) {
            notNull(handlebars, "The handlebars is required.");
            handlebars.registerHelper(name(), this);
        }
        /**
         * Register all the text helpers.
         *
         * @param handlebars The helper's owner. Required.
         */
        public static void register(final Handlebars handlebars) {
            notNull(handlebars, "A handlebars object is required.");
            MathsHelpers[] helpers = values();
            for (MathsHelpers helper : helpers) {
                helper.registerHelper(handlebars);
            }
        }
    }

    public static class HBLogger extends LogHelper {
        private static final Logger log = LoggerFactory.getLogger(Handlebars.class);
        /**
         * A singleton instance of this helper.
         */
        public static final Helper<Object> INSTANCE = new HBLogger();

        /**
         * The helper's name.
         */
        public static final String NAME = "log";

        @Override
        public Object apply(Object context, Options options) throws IOException {
            StringBuilder sb = new StringBuilder();
            String level = options.hash("level", "info");
            TagType tagType = options.tagType;
            if (tagType.inline()) {
                sb.append(context);
                for (int i = 0; i < options.params.length; i++) {
                    sb.append(" ").append((Object) options.param(i));
                }
            } else {
                sb.append(options.fn());
            }
            switch (level) {
                case "error":
                    log.error(sb.toString().trim());
                    break;
                case "debug":
                    log.debug(sb.toString().trim());
                    break;
                case "warn":
                    log.warn(sb.toString().trim());
                    break;
                case "trace":
                    log.trace(sb.toString().trim());
                    break;
                default:
                    log.info(sb.toString().trim());
            }
            return null;
        }
    }

    /**
     * A mark-down helper using FlexMark.
     * */
    public static class MarkdownHelper implements Helper<Object> {
        private static final MutableDataSet MD_OPTIONS = new MutableDataSet();
        private static final Parser PARSER = Parser.builder(MD_OPTIONS).build();
        private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder(MD_OPTIONS).build();

        /**
         * A singleton version of {@link MarkdownHelper}.
         */
        public static final Helper<Object> INSTANCE = new MarkdownHelper();
        /**
         * The helper's name.
         */
        public static final String NAME = "markdown";

        @Override
        public Object apply(final Object context, final Options options) {
            if (options.isFalsy(context)) {
                return "";
            }
            String markdown = context.toString();
            Node document = PARSER.parse(markdown);
            return new Handlebars.SafeString(HTML_RENDERER.render(document));
        }
    }
    /**
     * String helpers not included in StringComparison.
     * */
    public enum StringComparison implements Helper<Object> {

        startsWith {
            @Override
            protected boolean safeApply(final Object value, final Options options) {
                return Strings.CS.startsWith(value.toString(), options.param(0)) ? options.hash("yes", true) : options.hash("no", false);
            }
        },
        endsWith {
            @Override
            protected boolean safeApply(final Object value, final Options options) {
                return Strings.CS.endsWith(value.toString(), options.param(0)) ? options.hash("yes", true) : options.hash("no", false);
            }
        },
        contains {
            @Override
            protected boolean safeApply(final Object value, final Options options) {
                return Strings.CS.contains(value.toString(), options.param(0)) ? options.hash("yes", true) : options.hash("no", false);
            }
        },
        startsWithCI {
            @Override
            protected boolean safeApply(final Object value, final Options options) {
                return Strings.CI.startsWith(value.toString(), options.param(0)) ? options.hash("yes", true) : options.hash("no", false);
            }
        },
        endsWithCI {
            @Override
            protected boolean safeApply(final Object value, final Options options) {
                return Strings.CI.endsWith(value.toString(), options.param(0)) ? options.hash("yes", true) : options.hash("no", false);
            }
        },
        containsCI {
            @Override
            protected boolean safeApply(final Object value, final Options options) {
                return Strings.CI.contains(value.toString(), options.param(0)) ? options.hash("yes", true) : options.hash("no", false);
            }
        },

        ;
        @Override
        public Object apply(final Object context, final Options options) {
            if (options.isFalsy(context)) {
                Object param = options.param(0, null);
                return param == null ? null : param.toString();
            }
            return safeApply(context, options);
        }

        /**
         * Apply the helper to the context.
         *
         * @param context The context object (param=0).
         * @param options The options object.
         * @return A string result.
         */
        protected abstract boolean safeApply(Object context, Options options);

        /**
         * Register the helper in a handlebars instance.
         *
         * @param handlebars A handlebars object. Required.
         */
        public void registerHelper(final Handlebars handlebars) {
            notNull(handlebars, "The handlebars is required.");
            handlebars.registerHelper(name(), this);
        }

        /**
         * Register all the text helpers.
         *
         * @param handlebars The helper's owner. Required.
         */
        public static void register(final Handlebars handlebars) {
            notNull(handlebars, "A handlebars object is required.");
            StringComparison[] helpers = values();
            for (StringComparison helper : helpers) {
                helper.registerHelper(handlebars);
            }
        }
    }
}

