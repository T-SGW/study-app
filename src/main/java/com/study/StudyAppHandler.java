package com.study;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class StudyAppHandler implements RequestHandler<Map<String, Object>, String> {
    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        try {
            String body = (String) input.get("body");
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> data = mapper.readValue(body, Map.class);

            String message = (String) data.getOrDefault("message", "なし");
            String user = (String) data.getOrDefault("user", "匿名");

            return "勉強アプリ受信: メッセージ=" + message + ", ユーザー=" + user;
        } catch (Exception e) {
            return "エラー: " + e.getMessage();
        }
    }
}
