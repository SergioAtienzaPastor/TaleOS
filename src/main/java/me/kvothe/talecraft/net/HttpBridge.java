package me.kvothe.talecraft.net;

import java.util.concurrent.CompletableFuture;

public interface HttpBridge {
    boolean isEnabled();

    CompletableFuture<HttpResult> get(Object owner, String url);

    void cancel(Object owner);
}
