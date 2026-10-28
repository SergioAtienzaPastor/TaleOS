package me.kvothe.talecraft.net;

public record HttpResult(int statusCode, String reason, String body) {
    public HttpResult {
        reason = me.kvothe.talecraft.I18n.t(reason);
    }
}
