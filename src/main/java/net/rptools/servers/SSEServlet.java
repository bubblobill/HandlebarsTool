package net.rptools.servers;

import net.rptools.data.SheetsObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import org.eclipse.jetty.server.Server;

class SSEServlet extends HttpServlet {
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

        PrintWriter writer = resp.getWriter();

        // Simple event stream: send current time every second
        System.out.println("#");
//        final Server server_ = server;
//        synchronized (server_) {
            while (server != null && server.isRunning()) {
                if (SheetsObject.getWatchChange()) {
                    System.out.println("&");
                    // SSE format:
                    // event: <event-name> (optional)
                    // data: <data> (required)
                    // Note double line break to separate events

//                    writer.print("event: refresh\n");
                    writer.print("data: true\n\n");
                    writer.flush();
                    System.out.println("Update notification sent");
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
