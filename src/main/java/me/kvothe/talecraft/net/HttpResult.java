package me.kvothe.talecraft.net;

public record HttpResult(int statusCode, String reason, String body) {
}
