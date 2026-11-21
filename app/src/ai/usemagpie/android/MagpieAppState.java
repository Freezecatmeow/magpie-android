package ai.usemagpie.android;

/** Process-wide shared state between MagpieGatewayService and MainActivity (same app process). */
public final class MagpieAppState {
    public static volatile boolean running = false;
    public static volatile int port = 3430;
    public static volatile String webKey = "";
    public static volatile String lanIp = "";
    public static volatile int bridgePort = 0;
    public static volatile String proxyHost = "";
    public static volatile int proxyPort = 0;
    public static volatile String caPath = "";
    private MagpieAppState() {}
}
