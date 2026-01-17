package net.rptools.servers;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.rptools.data.SheetsObject;
import net.rptools.data.config.Config;
import net.rptools.data.config.Pref;
import org.eclipse.jetty.servlets.EventSource;
import org.eclipse.jetty.servlets.EventSourceServlet;
import org.eclipse.jetty.util.resource.PathResourceFactory;
import org.eclipse.jetty.util.thread.AutoLock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.*;

import static net.rptools.servers.OneServlet.TEMPLATE_DATA_CHANGED;

public class SSEServlet {//  extends EventSourceServlet {
//        private static final byte[] CRLF = new byte[]{'\r', '\n'};
//        private static final byte[] EVENT_FIELD = "event: ".getBytes(StandardCharsets.UTF_8);
//        private static final byte[] DATA_FIELD = "data: ".getBytes(StandardCharsets.UTF_8);
//        private static final byte[] COMMENT_FIELD = ": ".getBytes(StandardCharsets.UTF_8);
//
//        private ScheduledExecutorService scheduler;
//        private final int heartBeatPeriod;
//        private final TimeUnit timeUnit;
//
//        public SSEServlet(int heartBeatPeriod, TimeUnit timeUnit){
//            this.heartBeatPeriod = heartBeatPeriod;
//            this.timeUnit = timeUnit;
//        }
//        @Override
//        public void init() throws ServletException {
//            scheduler = Executors.newSingleThreadScheduledExecutor();
//        }
//
//        @Override
//        public void destroy() {
//            if (scheduler != null) {
//                scheduler.shutdown();
//            }
//        }
//
//        @Override
//        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//            @SuppressWarnings("unchecked")
//            Enumeration<String> acceptValues = request.getHeaders("Accept");
//            while (acceptValues.hasMoreElements()) {
//                String accept = acceptValues.nextElement();
//                if (accept.equals("text/event-stream")) {
//                    EventSource eventSource = newEventSource(request);
//                    if (eventSource == null) {
//                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
//                    } else {
//                        respond(request, response);
//                        AsyncContext async = request.startAsync();
//                        // Infinite timeout because the continuation is never resumed, but only completed on close
//                        async.setTimeout(0);
//                        SSEServlet.EventSourceEmitter emitter = new SSEServlet.EventSourceEmitter(eventSource, async);
//                        emitter.scheduleHeartBeat();
//                        open(eventSource, emitter);
//                    }
//                    return;
//                }
//            }
//            super.doGet(request, response);
//        }
//
//        protected EventSource newEventSource(HttpServletRequest request){
//            return new EventSource() {
//                @Override
//                public void onOpen(Emitter emitter_) throws IOException {
//                    SSEServlet.EventSourceEmitter emitter = (EventSourceEmitter) emitter_;
//                    if(SheetsObject.getWatchChange()){
//                        emitter.data("{\"source-change\":true, \"template-change\":false, \"idle\": false}");
//                        SheetsObject.setWatchChange(false);
//                    } else if(TEMPLATE_DATA_CHANGED.get()) {
//                        emitter.data("{\"source-change\":false, \"template-change\":true, \"idle\": false}");
//                        TEMPLATE_DATA_CHANGED.set(false);
//                    } else {
//                        emitter.data("{\"source-change\":false, \"template-change\":false, \"idle\": true}");
//                    }
//                }
//                @Override public void onClose() { }
//            };
//        }
//
//        protected void respond(HttpServletRequest request, HttpServletResponse response) throws IOException {
//            response.setStatus(HttpServletResponse.SC_OK);
//            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
//            response.setContentType("text/event-stream");
//            // By adding this header, and not closing the connection,
//            // we disable HTTP chunking, and we can use write()+flush()
//            // to send data in the text/event-stream protocol
//            response.addHeader("Connection", "close");
//            response.flushBuffer();
//        }
//
//        protected void open(EventSource eventSource, EventSource.Emitter emitter) throws IOException {
//            eventSource.onOpen(emitter);
//        }
//
//        protected class EventSourceEmitter implements EventSource.Emitter, Runnable {
//            private final AutoLock lock = new AutoLock();
//            private final EventSource eventSource;
//            private final AsyncContext async;
//            private final ServletOutputStream output;
//            private Future<?> heartBeat;
//            private boolean closed;
//
//            public EventSourceEmitter(EventSource eventSource, AsyncContext async) throws IOException {
//                this.eventSource = eventSource;
//                this.async = async;
//                this.output = async.getResponse().getOutputStream();
//            }
//
//            @Override
//            public void event(String name, String data) throws IOException {
//                try (AutoLock l = lock.lock()) {
//                    output.write(EVENT_FIELD);
//                    output.write(name.getBytes(StandardCharsets.UTF_8));
//                    output.write(CRLF);
//                    data(data);
//                }
//            }
//
//            @Override
//            public void data(String data) throws IOException {
//                try (AutoLock l = lock.lock()) {
//                    BufferedReader reader = new BufferedReader(new StringReader(data));
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        output.write(DATA_FIELD);
//                        output.write(line.getBytes(StandardCharsets.UTF_8));
//                        output.write(CRLF);
//                    }
//                    output.write(CRLF);
//                    flush();
//                }
//            }
//
//            @Override
//            public void comment(String comment) throws IOException {
//                try (AutoLock l = lock.lock()) {
//                    output.write(COMMENT_FIELD);
//                    output.write(comment.getBytes(StandardCharsets.UTF_8));
//                    output.write(CRLF);
//                    output.write(CRLF);
//                    flush();
//                }
//            }
//
//            @Override
//            public void run() {
//                // If the other peer closes the connection, the first
//                // flush() should generate a TCP reset that is detected
//                // on the second flush()
//                try {
//                    try (AutoLock l = lock.lock()) {
//                        output.write('\r');
//                        flush();
//                        output.write('\n');
//                        flush();
//                    }
//                    // We could write, reschedule heartbeat
//                    scheduleHeartBeat();
//                } catch (IOException x) {
//                    // The other peer closed the connection
//                    close();
//                    eventSource.onClose();
//                }
//            }
//
//            protected void flush() throws IOException {
//                async.getResponse().flushBuffer();
//            }
//
//            @Override
//            public void close() {
//                try (AutoLock l = lock.lock()) {
//                    closed = true;
//                    heartBeat.cancel(false);
//                }
//                async.complete();
//            }
//
//            private void scheduleHeartBeat() {
//                try (AutoLock l = lock.lock()) {
//                    if (!closed) {
//                        heartBeat = scheduler.schedule(this, heartBeatPeriod, timeUnit);
//                    }
//                }
//            }
//        }
    }
