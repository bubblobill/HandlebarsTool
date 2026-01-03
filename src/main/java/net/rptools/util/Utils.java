package net.rptools.util;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.helper.*;
import com.github.jknack.handlebars.server.HbsServer;
import net.rptools.servers.MapToolHelpers;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.handler.ErrorHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class Utils {
    private static final String CHARSET_ENCODING = StandardCharsets.ISO_8859_1.name();
    private static final String CONTENT_TYPE = "text/html";

    public static void registerHandlebarsHelpers(Handlebars handlebars) {
        // ---- HELPERS ----
        handlebars.registerHelper(HelperRegistry.HELPER_MISSING, (context, options) -> new Handlebars.SafeString(options.fn.text()));
        handlebars.registerHelper("json", Jackson2Helper.INSTANCE);
        StringHelpers.register(handlebars);
        HumanizeHelper.register(handlebars);
        Arrays.stream(ConditionalHelpers.values()).forEach(h -> handlebars.registerHelper(h.name(), h));
        NumberHelper.register(handlebars);
        handlebars.registerHelper(AssignHelper.NAME, AssignHelper.INSTANCE);
        handlebars.registerHelper(IncludeHelper.NAME, IncludeHelper.INSTANCE);
        Arrays.stream(MapToolHelpers.values()).forEach(h -> handlebars.registerHelper(h.name(), h));
    }

    public static void commonResponseBits(HttpServletResponse response) {
        response.addHeader("Expires", "Sat, 26 Jul 1997 05:00:00 GMT");
        response.addHeader("Cache-control", "no-cache");
        response.addHeader("Cache-control", "no-store");
        response.addHeader("Pragma", "no-cache");
        response.addHeader("Clear-Site-Data", "*");
        response.addHeader("Sec-GPC", "1");
        response.setCharacterEncoding(CHARSET_ENCODING);
        response.setContentType(CONTENT_TYPE);
    }


    public static final Supplier<ErrorHandler> errorHandlerSupplier = () -> new ErrorHandler() {
        @Override
        protected void writeErrorPageHead(final HttpServletRequest request, final Writer writer,
                                          final int code, final String message) throws IOException {
            writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"/>\n");
            writer.write("<title>{{");
            writer.write(Integer.toString(code));
            writer.write("}}");
            writer.write("</title>\n");
            writer.write("<style>body{font-family: monospace;}</style>");
        }

        @Override
        protected void writeErrorPageMessage(final HttpServletRequest request, final Writer writer,
                                             final int code,
                                             final String message, final String uri) throws IOException {
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

        @Override
        protected void writeErrorPageBody(final HttpServletRequest request, final Writer writer,
                                          final int code,
                                          final String message, final boolean showStacks) throws IOException {
            String uri = request.getRequestURI();
            writeErrorPageMessage(request, writer, code, message, uri);
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
                .combine("version", HbsServer.version)
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
        error.put("evidence", evidence);
        root.put("error", error);
        int firstLine = Math.max(1, ex.getLocation().getLineNr() - 1);
        fancyError(root, firstLine, response);
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
            return IOUtils.toString(input, StandardCharsets.ISO_8859_1);
        } finally {
            IOUtils.closeQuietly(input);
        }
    }

    public static boolean prompt(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, null, JOptionPane.YES_NO_OPTION) == JOptionPane.OK_OPTION;
    }

    private static final JOptionPane optionPane = new JOptionPane(null, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION);
    private static JDialog dialogue = null;
    private static final JPanel messagePanel = new JPanel();
    static {
        optionPane.setOptions(new Object[]{"Okay"});
        optionPane.setMessage(messagePanel);
        BoxLayout box = new BoxLayout(messagePanel, BoxLayout.PAGE_AXIS);
        messagePanel.setLayout(box);
        optionPane.setVisible(false);
    }
    public static void alert(String notice, String... messages){
        if(optionPane.isVisible()){
            return;
        }
        messagePanel.removeAll();
        messagePanel.add(new JLabel(String.format("<html><h2 color=\"blue\">%s</h2></html>", notice)));
        StringBuilder builder = new StringBuilder("<html>");
        for (String message : messages) {
            builder.append("<p>").append(message).append("</p>");
        }
        builder.append("</html>");
        messagePanel.add(new JLabel(builder.toString()));
        new Thread(()->{
            JOptionPane.showMessageDialog(null, messagePanel);
            optionPane.setVisible(false);
        }).start();
    }
    public static void whoops(Throwable e) {
        if (dialogue != null || optionPane.isVisible()) {
            return;
        }
        messagePanel.removeAll();
        messagePanel.add(new JLabel(e.getLocalizedMessage()));
        StackTraceElement[] elements = e.getStackTrace();
        for (int i = 0; i < Math.min(12, elements.length); i++) {
            messagePanel.add(new JLabel(String.format("%s", elements[i].toString())));
        }
        optionPane.setVisible(true);
        dialogue = optionPane.createDialog("Error");
        dialogue.setModal(true);
        dialogue.pack();
        new Thread(()->{
            dialogue.setVisible(true);
            dialogue = null;
            optionPane.setVisible(false);
        }).start();
    }
}

