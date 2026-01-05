package net.rptools.servers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.JsonNodeValueResolver;
import com.github.jknack.handlebars.Template;
import net.rptools.data.TemplateData;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import net.rptools.util.Utils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

import static net.rptools.data.Constants.OBJECT_MAPPER;
import static net.rptools.data.TemplateData.TEMPLATE_DATA;

public class TestServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(TestServlet.class);
    private final Template template;

    private static final ObjectReader TEMPLATE_UPDATER = OBJECT_MAPPER.readerForUpdating(TEMPLATE_DATA);
    public TestServlet(Template template){
        super();
        this.template = template;
    }


    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {
        log.info("Test Server: POST Request - > {}", request.getRequestURI());
        try (BufferedReader reader = request.getReader()) { // try-with-resources auto-closes the reader
            TEMPLATE_UPDATER.readValue(reader);
            TemplateData.filterProperties();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        log.info("Test Server: GET Request - > {}", request.getRequestURI());
        Utils.commonResponseBits(response);
        Writer writer = null;
        try {
            Context context = Context
                    .newBuilder(TEMPLATE_DATA)
                    .push(JsonNodeValueResolver.INSTANCE)
                    .build();
            String output = template.apply(context);

            writer = response.getWriter();
            writer.write(output);
        } catch (HandlebarsException ex) {
            Utils.handlebarsError(ex, response);
        } catch (JsonParseException ex) {
            log.error("Unexpected error", ex);
        } catch (FileNotFoundException ex) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException | RuntimeException ex) {
            log.error("Unexpected error", ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error", ex);
            throw new ServletException(ex);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }
}
