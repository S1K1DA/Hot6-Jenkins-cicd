package com.example.hot6novelcraft.domain.reviewai.client;

import com.example.hot6novelcraft.domain.reviewai.dto.response.AiReviewResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI 호출 → AI 리뷰(댓글마다 평점 포함) 생성
 * - Spring AI 1.0.0 정식 버전 사용
 * - 모델/옵션은 yaml이 아닌 이 클래스에서 직접 지정 (팀원별 다른 모델 충돌 방지)
 * - 매번 호출 시 새로운 결과 생성 (캐싱 없음)
 */
@Slf4j
@Component
public class AiReviewClient {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiReviewClient(OpenAiChatModel openAiChatModel, ObjectMapper objectMapper) {

        // 이 기능 전용 옵션 (다른 팀원 코드와 독립적)
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.9)
                .build();

        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultOptions(options)
                .build();

        this.objectMapper = objectMapper;
    }


    // 회차 본문 기반 AI 리뷰 생성
    public AiReviewResponse generate(Long episodeId, String title, String content) {

        // 프롬프트 구성
        String prompt = buildPrompt(title, content);

        // OpenAI 호출
        String json;
        try {
            json = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.debug("[AI 리뷰 응답] episodeId={}, json={}", episodeId, json);
        } catch (RuntimeException e) {
            log.error("[AI 리뷰 호출 실패] episodeId={}", episodeId, e);
            throw new IllegalStateException("AI 리뷰 생성 실패", e);
        }

        // JSON 파싱
        return parseResponse(episodeId, json);
    }

    private String buildPrompt(String title, String content) {
        return """
                당신은 웹소설 플랫폼의 다양한 독자들입니다.
                작가님이 발행하기 전에 받아보는 미리 보기 리뷰입니다.
                실제 독자들이 다는 댓글처럼 자연스러운 반응을 남겨주세요.

                [회차 제목]
                %s

                [본문]
                %s

                [규칙]
                - 댓글은 6~10개 사이로 생성
                - 각 댓글마다 댓글 작성자가 매긴 개별 평점(rating) 포함 (1.0 ~ 5.0, 0.5 단위)
                - 댓글 길이는 반드시 다양하게 섞어서 작성:
                  * 짧은 댓글 (10~30자): "와 미쳤다 😭", "다음화 언제요ㅠㅠ" 같은 즉각적 반응
                  * 중간 댓글 (40~80자): 한 가지 포인트에 대한 감상
                  * 긴 댓글 (90~150자): 인물/전개/감정선에 대한 상세한 감상평
                - 짧은거랑 긴거를 적절히 섞을 것 (한 종류만 나오면 안됨)
                - 모두 구어체, 이모지 자연스럽게 사용 (😭, ㅠㅠ, ㅋㅋ, ㄷㄷ, !! 등)
                - 본문 내용에 기반한 반응 (등장인물 이름, 전개, 감정선 등 구체적으로 언급)
                - 평론가처럼 분석하지 말 것 (예: "이 작품은 ~한 의미가 있다" X)
                - 닉네임은 실제 웹소설 독자 느낌으로 (예: 달빛독자, 소설덕후777, 띵작헌터, 결말이뭐야, 정주행중, 새벽감성러 등)
                - 닉네임은 3~10자, 매번 다르게

                [평가 기준 - 반드시 본문 퀄리티에 맞춰 솔직하고 냉정하게]
                - 절대 무조건 호의적으로 평가하지 말 것. 작가에게 도움되는 솔직한 피드백이 목적
                - 본문 퀄리티에 따라 댓글마다 평점 차등 적용:
                  * 1.0 ~ 1.5: 본문이 의미 없는 글 (테스트 입력, 같은 단어 반복, "asdf", "ㅁㄴㅇㄹ" 등 무의미)
                  * 2.0 ~ 2.5: 내용은 있으나 매우 부실 (글자수 부족, 맥락 없음, 비문 다수, 흐름 X)
                  * 3.0 ~ 3.5: 평범 (그럭저럭 읽히지만 특별함 없음, 클리셰, 진부함)
                  * 4.0 ~ 4.5: 잘 쓴 글 (몰입감, 캐릭터 매력, 흐름 자연스러움)
                  * 5.0: 정말 뛰어난 글 (완성도, 독창성, 감동)
                - 댓글마다 평점이 약간씩 다른게 자연스러움 (어떤 독자는 4.0, 어떤 독자는 4.5 이런식)
                - 부정적 댓글이면 낮은 평점, 긍정적 댓글이면 높은 평점 (댓글 내용과 평점이 일치해야 함)
                - 별로면 별로라고 솔직하게 댓글 작성. 예시:
                  * "이게 뭐임... 내용이 없는데요?", "테스트하시는거 아니죠?ㅋㅋ"
                  * "흐음... 도입부가 너무 평범해요. 좀 더 흥미로운 시작 어떨까요?"
                  * "캐릭터가 너무 평면적이에요 ㅠㅠ 깊이가 부족한 느낌"
                  * "오타랑 비문이 좀 많네요... 검수 한번 더 부탁드려요!"
                  * "스토리가 어디로 가는지 잘 모르겠어요. 흐름 정리가 필요할듯"
                - 비판할 때도 인신공격은 하지 말 것 (작품에 대한 피드백만)
                - 좋은 점이 있으면 좋은 댓글, 아쉬운 점이 있으면 아쉬운 댓글 자연스럽게 섞기

                [좋은 예시 - 잘 쓴 글에 대한 반응]
                짧은: {"nickname": "달빛독자", "content": "와 진짜 미쳤다 😭😭", "rating": 5.0}
                중간: {"nickname": "소설덕후", "content": "주인공 각성 장면 진짜 소름이었어요 ㄷㄷ 다음화 빨리 보고싶다", "rating": 4.5}
                긴: {"nickname": "새벽감성러", "content": "이번화에서 주인공이 친구를 배신하는 장면 너무 충격적이었어요ㅠㅠ 그동안 쌓아온 우정이 한순간에 무너지는 느낌... 작가님 감정선 진짜 잘 살리시는듯 ㅋㅋ", "rating": 4.5}

                [냉정한 예시 - 부실한 글에 대한 반응]
                짧은: {"nickname": "독자123", "content": "음... 이게 끝인가요?", "rating": 1.5}
                중간: {"nickname": "결말이뭐야", "content": "도입부 너무 갑작스러워요. 인물 소개가 부족한 느낌이에요", "rating": 2.5}
                긴: {"nickname": "정주행중", "content": "솔직히 흐름이 좀 끊기는 느낌이에요. 캐릭터 동기도 약하고 갈등 구조도 평면적이라 몰입이 잘 안됐어요. 다음화에서 좀 더 디테일하게 풀어주시면 좋을것 같아요!", "rating": 2.0}

                [응답 형식 - 반드시 아래 JSON만 출력. 코드블럭이나 다른 말 절대 금지]
                {
                  "comments": [
                    {"nickname": "달빛독자", "content": "와 미쳤다 😭", "rating": 5.0},
                    {"nickname": "소설덕후777", "content": "주인공 각성 장면 진짜 소름이었어요 ㄷㄷ 다음화 빨리 보고싶다", "rating": 4.5}
                  ]
                }
                """.formatted(
                title == null ? "" : title,
                content == null ? "" : content
        );
    }

    private AiReviewResponse parseResponse(Long episodeId, String json) {
        try {
            // OpenAI가 가끔 ```json ... ``` 으로 감싸는 경우 방어
            String cleaned = json.trim()
                    .replaceAll("(?s)^```json", "")
                    .replaceAll("(?s)^```", "")
                    .replaceAll("(?s)```$", "")
                    .trim();

            ParsedAiResponse parsed = objectMapper.readValue(cleaned, ParsedAiResponse.class);

            // 댓글마다 평점 보정 (혹시 5점 초과나 1점 미만 응답시)
            List<AiReviewResponse.AiCommentResponse> comments = parsed.comments.stream()
                    .map(c -> {
                        double rating = Math.max(1.0, Math.min(5.0, c.rating));
                        return new AiReviewResponse.AiCommentResponse(c.nickname, c.content, rating);
                    })
                    .toList();

            return new AiReviewResponse(episodeId, comments);

        } catch (JsonProcessingException e) {
            log.error("[AI 리뷰 파싱 실패] episodeId={}, json={}", episodeId, json, e);
            throw new IllegalStateException("AI 리뷰 파싱 실패", e);
        }
    }

    // 파싱용 내부 DTO
    private record ParsedAiResponse(List<ParsedComment> comments) {}

    private record ParsedComment(String nickname, String content, double rating) {}
}