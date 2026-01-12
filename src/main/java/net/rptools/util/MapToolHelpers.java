package net.rptools.util;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public enum MapToolHelpers implements Helper<Object> {
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
    base64Encode {
        @Override
        public Object apply(final Object context, final Options options) {
            byte[] message = context.toString().getBytes(StandardCharsets.UTF_8);

            return new Handlebars.SafeString(Base64.getUrlEncoder().encodeToString(message));
        }
    }
}
