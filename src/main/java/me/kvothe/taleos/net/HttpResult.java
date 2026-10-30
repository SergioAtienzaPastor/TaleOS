package me.kvothe.taleos.net;

public record HttpResult(int statusCode, String reason, String body) {
    public HttpResult {
        reason = me.kvothe.taleos.I18n.t(reason);
    }
}
