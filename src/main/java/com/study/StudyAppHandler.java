package com.study;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StudyAppHandler implements RequestHandler<Map<String, Object>, String> {

    // DynamoDB クライアントを初期化
    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = "StudyRecords";

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        try {
            // HTTP メソッドの判定（GET or POST）
            Map<String, Object> requestContext = (Map<String, Object>) input.get("requestContext");
            Map<String, String> http = (Map<String, String>) requestContext.get("http");
            String method = http.get("method");

            if ("POST".equalsIgnoreCase(method)) {
                // ✅ POSTリクエスト: 勉強記録を保存
                String body = (String) input.get("body");
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> data = mapper.readValue(body, Map.class);

                String user = (String) data.getOrDefault("user", "unknown");
                String message = (String) data.getOrDefault("message", "none");
                String date = (String) data.getOrDefault("date", "1970-01-01");

                // DynamoDB に保存するアイテムを作成
                Map<String, AttributeValue> item = new HashMap<>();
                item.put("user", AttributeValue.builder().s(user).build());
                item.put("date", AttributeValue.builder().s(date).build());
                item.put("message", AttributeValue.builder().s(message).build());

                PutItemRequest request = PutItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .item(item)
                        .build();

                dynamoDb.putItem(request);

                return "保存成功: ユーザー=" + user + ", 日付=" + date + ", メッセージ=" + message;

            } else if ("GET".equalsIgnoreCase(method)) {
                // ✅ GETリクエスト: ユーザーの勉強記録を取得
                Map<String, String> queryParams = (Map<String, String>) input.get("queryStringParameters");
                if (queryParams == null || !queryParams.containsKey("user")) {
                    return "エラー: user パラメータが必要です";
                }

                String user = queryParams.get("user");
                if (queryParams.containsKey("date")) {
                    // 🔹 user + date → 単一取得
                    String date = queryParams.get("date");

                    // 主キー（user + date）で取得
                    Map<String, AttributeValue> key = new HashMap<>();
                    key.put("user", AttributeValue.builder().s(user).build());
                    key.put("date", AttributeValue.builder().s(date).build());

                    GetItemRequest getRequest = GetItemRequest.builder()
                            .tableName(TABLE_NAME)
                            .key(key)
                            .build();

                    GetItemResponse response = dynamoDb.getItem(getRequest);

                    if (response.item() == null || response.item().isEmpty()) {
                        return "記録が見つかりませんでした";
                    }

                    // DynamoDBから取得した属性値を適切に抽出
                    String message = response.item().get("message").s();
                    

                    return "取得成功: ユーザー=" + user + ", 日付=" + date + ", メッセージ=" + message;
                } else {
                    // 🔹 user のみ → 履歴一覧取得（Query 使用）
                    QueryRequest queryRequest = QueryRequest.builder()
                            .tableName(TABLE_NAME)
                            .keyConditionExpression("#usr = :userVal")
                            .expressionAttributeNames(Map.of("#usr", "user"))
                            .expressionAttributeValues(Map.of(":userVal", AttributeValue.builder().s(user).build()))
                            .scanIndexForward(false) // 新しい順（降順）
                            .build();

                    QueryResponse response = dynamoDb.query(queryRequest);

                    if (response.items().isEmpty()) {
                        return "履歴が見つかりませんでした（ユーザー=" + user + "）";
                    }

                    // 一覧を文字列に整形
                    List<String> resultList = response.items().stream()
                            .map(item -> {
                                String date = item.get("date").s();
                                String msg = item.get("message").s();
                                return "📅" + date + ": " + msg;
                            })
                            .collect(Collectors.toList());

                    return "履歴一覧（ユーザー=" + user + "）:\n" + String.join("\n", resultList);
                }
            }

            return "サポートされていない HTTP メソッド: " + method;

        } catch (Exception e) {
            return "エラー: " + e.getMessage();
        }
    }
}
