package com.campusflow;

import com.campusflow.map.AmapProxyController;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmapProxyResponseTest {
    @Test void jsonpUsesExecutableMimeButPlainJsonKeepsJsonMime() throws Exception {
        var controller=new AmapProxyController("test-key","test-code");
        var client=mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<byte[]> upstream=mock(HttpResponse.class);
        when(upstream.statusCode()).thenReturn(200);
        when(upstream.headers()).thenReturn(HttpHeaders.of(java.util.Map.of("Content-Type",java.util.List.of("application/json;charset=UTF-8")),(a,b)->true));
        when(client.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(upstream);
        ReflectionTestUtils.setField(controller,"client",client);
        var request=new MockHttpServletRequest("GET","/_AMapService/v3/assistant/coordinate/convert");
        request.addParameter("callback","jsonp_test");
        when(upstream.body()).thenReturn("jsonp_test({\"status\":\"1\"})".getBytes(StandardCharsets.UTF_8));
        assertEquals("application/javascript;charset=UTF-8",controller.proxy(request).getHeaders().getFirst("Content-Type"));
        request.removeParameter("callback");
        when(upstream.body()).thenReturn("{\"status\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("application/json;charset=UTF-8",controller.proxy(request).getHeaders().getFirst("Content-Type"));
        request.addParameter("callback","jsonp_test");
        assertEquals("application/json;charset=UTF-8",controller.proxy(request).getHeaders().getFirst("Content-Type"));
    }
}
