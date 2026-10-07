package main.com.chat.wechat.realtime.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Captures the server-resolved peer address during the HTTP handshake. It does
 * not inspect STOMP headers, so a browser cannot replace it with spoofed XFF.
 */
public class WebSocketClientIpHandshakeInterceptor implements HandshakeInterceptor {
	public static final String CLIENT_IP_ATTRIBUTE = "webchat.client-ip";

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		InetSocketAddress remoteAddress = request.getRemoteAddress();
		String clientIp = remoteAddress == null
				? "unknown"
				: remoteAddress.getAddress() == null
						? remoteAddress.getHostString()
						: remoteAddress.getAddress().getHostAddress();
		attributes.put(CLIENT_IP_ATTRIBUTE, clientIp);
		return true;
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception) {
		// No cleanup is required; the attributes are scoped to the WebSocket session.
	}
}
