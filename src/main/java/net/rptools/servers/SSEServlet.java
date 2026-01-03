package net.rptools.servers;

import net.rptools.data.SheetsObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SSEServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(SSEServlet.class);

    private Server server;

    public void setServer(Server server) {
        this.server = server;
    }

    @Override
    protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Set content type for SSE
        resp.setContentType("text/event-stream"); //most important part
        resp.setCharacterEncoding("UTF-8");
        // Disable caching
        resp.setHeader("X-Accel-Buffering", "no");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        log.info("SSEServlet request -> {}", req.getRequestURI());
        PrintWriter writer = resp.getWriter();

        while (server != null && server.isRunning()) {
            if (SheetsObject.getWatchChange()) {
                writer.print("data: true\n\n");
                writer.flush();
                log.info("SSEServlet -> Update notification sent");
                SheetsObject.setWatchChange(false);
            }
            try {
                wait(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        writer.close();
    }

}
