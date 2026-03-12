package net.rptools.util;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.helper.*;
import com.github.jknack.handlebars.helper.ext.AssignHelper;
import com.github.jknack.handlebars.helper.ext.IncludeHelper;
import com.github.jknack.handlebars.helper.ext.NumberHelper;
import com.github.jknack.handlebars.io.TemplateLoader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static net.rptools.util.HandlebarsHelpers.registerHelpers;

public class Utils {
    public static Handlebars createHandlebars(TemplateLoader templateLoader) {
        Handlebars handlebars = new Handlebars(templateLoader);
        handlebars.setStringParams(true);
        handlebars.setCharset(StandardCharsets.UTF_8);
        handlebars.parentScopeResolution(false);
        registerHelpers(handlebars);
        return handlebars;
    }

    public static void commonResponseBits(HttpServletResponse response) {
        response.addHeader("Expires", "Sat, 26 Jul 1997 05:00:00 GMT");
        response.addHeader("Cache-control", "no-cache");
        response.addHeader("Cache-control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
    }


    public static final Supplier<ErrorHandler> errorHandlerSupplier = () -> new ErrorHandler() {
        @Override
        protected void writeErrorHtmlHead(Request request, Writer writer, int code, String message) throws IOException {
            writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n");
            writer.write("<title>{{");
            writer.write(Integer.toString(code));
            writer.write("}}");
            writer.write("</title>\n");
            writer.write("<style>body{font-family: monospace;}</style>");
        }

        @Override
        protected void writeErrorHtmlMessage(Request request, Writer writer, int code, String message, Throwable cause, String uri) throws IOException {
            writer.write("<div align=\"center\">");
            writer.write("<p><span style=\"font-size: 48px;\">{{</span><span style=\"font-size: 36px; color:#999;\">");
            writer.write(Integer.toString(code));
            writer.write("</span><span style=\"font-size: 48px;\">}}</span></p>");
            writer.write("</h2>\n<p>Problem accessing ");
            write(writer, uri);
            writer.write(". Reason:\n<pre>    ");
            write(writer, message);
            writer.write("</pre></p>");
            writer.write("</div>");
            writer.write("<hr />");
        }
    };

    /**
     * Deal with a fancy errors.
     *
     * @param error     An error.
     * @param firstLine The first line to report.
     * @param response  The http response.
     * @throws IOException If something goes wrong.
     */
    public static void fancyError(final Object error, final int firstLine, final HttpServletResponse response) throws IOException {
        Handlebars handlebars = new Handlebars();
        StringHelpers.register(handlebars);
        Template template = handlebars.compile("/error-pages/error");
        PrintWriter writer = response.getWriter();
        template.apply(Context.newBuilder(error)
                .resolver(MapValueResolver.INSTANCE, FieldValueResolver.INSTANCE, JavaBeanValueResolver.INSTANCE)
                .combine("lang", "Xml")
                .combine("firstLine", firstLine).build(), writer);
        IOUtils.closeQuietly(writer);
    }

    /**
     * Deal with a {@link HandlebarsException}.
     *
     * @param ex       The handlebars' exception.
     * @param response The http response.
     * @throws IOException If something goes wrong.
     */
    public static void handlebarsError(final HandlebarsException ex, final HttpServletResponse response) throws IOException {
        HandlebarsError error = ex.getError();
        int firstLine = 1;
        if (error != null) {
            if (ex.getCause() != null) {
                firstLine = error.line;
            } else {
                firstLine = Math.max(1, error.line - 1);
            }
        }
        Utils.fancyError(ex, firstLine, response);
    }

    /**
     * Deal with a {@link HandlebarsException}.
     *
     * @param ex       The handlebars' exception.
     * @param request  The http request.
     * @param response The http response.
     * @throws IOException If something goes wrong.
     */
    public static void jsonError(final JsonParseException ex, final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> error = new HashMap<>();
        String filename = request.getRequestURI();
        JsonLocation location = ex.getLocation();
        String reason = ex.getMessage();
        int atIdx = reason.lastIndexOf(" at ");
        if (atIdx > 0) {
            reason = reason.substring(0, atIdx);
        }
        error.put("uri", filename);
        error.put("line", location.getLineNr());
        error.put("column", location.getColumnNr());
        error.put("reason", reason);
        error.put("type", "JSON error");
        String json = read(request, filename);
        var evidence = getStringBuilder(location, json);
        error.put("evidence", evidence);
        root.put("error", error);
        int firstLine = Math.max(1, ex.getLocation().getLineNr() - 1);
        fancyError(root, firstLine, response);
    }

    private static @NonNull StringBuilder getStringBuilder(JsonLocation location, String json) {
        StringBuilder evidence = new StringBuilder();
        int i = (int) location.getCharOffset();
        int nl = 0;
        while (i >= 0 && nl < 2) {
            char ch = json.charAt(i);
            if (ch == '\n') {
                nl++;
            }
            evidence.insert(0, ch);
            i--;
        }
        i = (int) location.getCharOffset() + 1;
        nl = 0;
        while (i < json.length() && nl < 2) {
            char ch = json.charAt(i);
            if (ch == '\n') {
                nl++;
            }
            evidence.append(ch);
            i++;
        }
        return evidence;
    }

    /**
     * Read a file from the servlet context.
     *
     * @param uri The requested file.
     * @return The string content.
     * @throws IOException If the file is not found.
     */
    public static String read(HttpServletRequest request, final String uri) throws IOException {
        InputStream input = null;
        try {
            input = request.getServletContext().getResourceAsStream(uri);
            if (input == null) {
                throw new FileNotFoundException(request.getServletPath() + uri);
            }
            return IOUtils.toString(input, StandardCharsets.UTF_8);
        } finally {
            IOUtils.closeQuietly(input);
        }
    }

    public static class HBLogger extends LogHelper {
        private static final Logger log = LoggerFactory.getLogger(HBLogger.class);
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
            System.out.println("Handlebars(" + level + "): " + sb.toString().trim());
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
}

