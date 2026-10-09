package com.campusflow.map;

import com.campusflow.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/amap")
public class AmapProxyController {
    private static final String PREFIX="/api/amap/_AMapService";
    private static final Map<String,String> ROUTES=Map.of(
        "/v3/place/text","restapi.amap.com", "/v3/geocode/regeo","restapi.amap.com",
        "/v3/assistant/coordinate/convert","restapi.amap.com", "/v3/ip","restapi.amap.com",
        "/v4/map/styles","webapi.amap.com", "/v3/vectormap","fmap01.amap.com");
    private final String key;
    private final String securityCode;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public AmapProxyController(@Value("${CF_AMAP_JS_KEY:}") String key,
        @Value("${CF_AMAP_SECURITY_JS_CODE:}") String securityCode) {
        this.key=key;this.securityCode=securityCode;
    }
    @GetMapping("/config") public Map<String,Object> config() {
        // 浏览器 Key 本身公开；安全密钥只能注入固定上游请求。
        boolean configured=!key.isBlank()&&!securityCode.isBlank();
        return Map.of("configured",configured,"key",configured?key:"");
    }
    @GetMapping("/_AMapService/**") public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String path=request.getRequestURI().substring(PREFIX.length());
        String host=ROUTES.get(path);
        if (host==null) throw new BusinessException(400,"MAP_PATH_NOT_ALLOWED","地图请求路径不受支持");
        if (key.isBlank()||securityCode.isBlank())
            throw new BusinessException(503,"MAP_NOT_CONFIGURED","管理员尚未配置高德地图服务");
        StringJoiner query=new StringJoiner("&");
        request.getParameterMap().forEach((name,values)->{
            if (!name.equalsIgnoreCase("key")&&!name.equalsIgnoreCase("jscode"))
                for (String value:values) query.add(encode(name)+"="+encode(value));
        });
        query.add("key="+encode(key));query.add("jscode="+encode(securityCode));
        try {
            var upstream=client.send(HttpRequest.newBuilder(URI.create("https://"+host+path+"?"+query))
                .timeout(Duration.ofSeconds(8)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            // 不转发重定向、Cookie 或包含凭据的上游异常信息。
            if (upstream.statusCode()!=200)
                throw new BusinessException(502,"MAP_UPSTREAM_UNAVAILABLE","地图服务暂不可用");
            return ResponseEntity.ok().header("Content-Type",upstream.headers().firstValue("Content-Type")
                .orElse("application/json")).header("Cache-Control","no-store").body(upstream.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502,"MAP_UPSTREAM_UNAVAILABLE","地图服务请求已中断");
        } catch (java.io.IOException|IllegalArgumentException e) {
            throw new BusinessException(502,"MAP_UPSTREAM_UNAVAILABLE","地图服务暂不可用");
        }
    }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
