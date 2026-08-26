package com.jt.learning.service.ai.client;

import com.jt.learning.common.ArticleLengthTier;
import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.service.ai.AiQuestionClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.prompt.ArticleGenreRoleRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockAiQuestionClient implements AiQuestionClient {

    private final ObjectMapper objectMapper;

    public MockAiQuestionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateQuestions(
            AiQuestionPrompt prompt,
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < request.questionCount(); i++) {
            questions.add(buildQuestion(request, sceneTagOptions, functionTagOptions, i));
        }

        try {
            return objectMapper.writeValueAsString(Map.of("questions", questions));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock AI JSON 序列化失败", exception);
        }
    }

    @Override
    public String generateArticle(AiQuestionPrompt prompt, AiArticleGenerationRequest request, String seed) {
        boolean english = "EN_TO_JA".equals(request.learningMode());
        List<String> sourceSentences = english ? List.of(
                "Our neighborhood library recently converted an unused archive room into a quiet evening study area for local residents.",
                "Instead of extending normal opening hours, it gives registered night-shift workers access through a separate entrance after closing.",
                "The first applicant was a nurse who often finishes work before sunrise and must wait for the first bus home.",
                "She previously rested in a convenience store, but now she can review her notes in a calm and well-lit room.",
                "Library staff discovered that evening users needed reliable lighting, power outlets, and security more than additional books.",
                "They therefore changed the cleaning schedule and stopped playing the recorded announcements that had disturbed people studying there.",
                "Some residents still worry about operating costs, so the library publishes monthly electricity use and attendance figures.",
                "The trial has not yet become permanent, but it has changed how the community thinks about access to public spaces.",
                "The debate continues because convenience, cost, fairness, and safety do not always point toward the same answer for every resident."
        ) : List.of(
                "我所在的社区图书馆最近把一间长期闲置的资料室改成了夜间阅览区。",
                "它不增加闭馆时间，而是让登记过的夜班工作者使用独立入口。",
                "第一位申请者是一名护士，她常在清晨下班后等待首班公交车。",
                "过去她只能在便利店休息，如今可以安静地整理学习笔记。",
                "管理员发现，夜间使用者需要的并不是更多书籍，而是稳定的灯光和插座。",
                "于是馆方调整了清洁时段，也取消了原先循环播放的提示广播。",
                "试行期间仍有人担心管理成本，社区因此每月公开用电量和使用记录。",
                "这项安排尚未决定是否长期保留，但它改变了人们对公共空间开放时间的理解。"
        );
        List<String> japaneseSentences = english ? List.of(
                "私の住む地域の図書館では最近、長く使われていなかった資料室を夜間閲覧室に改装しました。",
                "閉館時間を延ばすのではなく、登録した夜勤労働者が専用入口を利用する仕組みです。",
                "最初の申請者は看護師で、早朝に勤務を終えてから始発バスを待つことがよくあります。",
                "以前はコンビニで休むしかありませんでしたが、今は静かで明るい部屋でノートを復習できます。",
                "管理者は、夜間利用者に必要なのは本の追加よりも、安定した照明、コンセント、防犯対策だと気づきました。",
                "そこで図書館は清掃時間を調整し、勉強中の人を邪魔していた案内放送も取りやめました。",
                "運営費を心配する住民もいるため、図書館は毎月の電力使用量と利用者数を公開しています。",
                "この試みはまだ恒久的な制度ではありませんが、公共空間の利用に対する地域の考え方を変えました。",
                "利便性、費用、公平性、安全性がすべての住民に同じ答えを示すとは限らないため、議論は続いています。"
        ) : List.of(
                "私の住む地域の図書館では最近、長く使われていなかった資料室を夜間閲覧室に改装しました。",
                "閉館時間を延ばすのではなく、登録した夜勤労働者が専用入口を利用する仕組みです。",
                "最初の申請者は看護師で、早朝に勤務を終えてから始発バスを待つことがよくあります。",
                "以前はコンビニで休むしかありませんでしたが、今は静かに学習ノートを整理できます。",
                "管理者は、夜間利用者に必要なのは本の追加ではなく、安定した照明とコンセントだと気づきました。",
                "そこで図書館は清掃時間を調整し、繰り返し流していた案内放送も取りやめました。",
                "試行中も管理費を心配する声があるため、地域では毎月の電力使用量と利用記録を公開しています。",
                "この取り組みを続けるかは未定ですが、公共空間の開放時間に対する人々の考え方を変えました。"
        );
        int sentenceCount = switch (ArticleLengthTier.from(request.lengthTier())) {
            case SHORT -> 3;
            case MEDIUM -> english ? 6 : 5;
            case LONG -> sourceSentences.size();
        };
        List<Map<String, Object>> sentences = new ArrayList<>();
        for (int index = 0; index < sentenceCount; index++) {
            sentences.add(Map.of(
                    "index", index,
                    "chineseText", sourceSentences.get(index),
                    "japaneseReference", japaneseSentences.get(index)
            ));
        }
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("questionType", english
                ? "TRANSLATION_EN_TO_JA_ARTICLE" : "TRANSLATION_ZH_TO_JA_ARTICLE");
        article.put("contextText", english
                ? "A concise report about a community trial, written in a natural formal style."
                : "介绍社区公共空间试行安排，使用自然连贯的书面语。");
        article.put("level", request.level());
        article.put("difficulty", request.difficulty());
        article.put("grammarPoint", english
                ? "unused: 遊休（ゆうきゅう）\nnight shift: 夜勤（やきん）\npower outlet: コンセント\ntrial: 試行（しこう）"
                : "闲置：遊休（ゆうきゅう）\n夜班：夜勤（やきん）\n插座：コンセント\n试行：試行（しこう）");
        article.put("spoken", false);
        article.put("business", false);
        article.put("exam", false);
        article.put("sentences", sentences);
        Map<String, String> roles = new LinkedHashMap<>();
        ArticleGenreRoleRegistry.rolesFor(request.genreTagCode())
                .forEach((key, description) -> roles.put(key, "围绕夜间阅览区的" + description));
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("seed", seed);
        blueprint.put("coreConcept", "公共阅览空间与夜班工作者");
        blueprint.put("roles", roles);
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "blueprint", blueprint,
                    "article", article
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock 文章 AI JSON 序列化失败", exception);
        }
    }

    private Map<String, Object> buildQuestion(
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions,
            int index
    ) {
        AiQuestionTagOptionDTO sceneTag = sceneTagOptions.get(index % sceneTagOptions.size());
        List<String> tagCodes = new ArrayList<>();
        tagCodes.add(sceneTag.code());
        if (!functionTagOptions.isEmpty()) {
            tagCodes.add(functionTagOptions.get(index % functionTagOptions.size()).code());
        }

        Map<String, Object> question = new LinkedHashMap<>();
        boolean english = "EN_TO_JA".equals(request.learningMode());
        question.put("questionType", english ? "TRANSLATION_EN_TO_JA" : "TRANSLATION_ZH_TO_JA");
        question.put("sourceText", english ? buildEnglishSourceText(index) : buildSourceText(index));
        question.put("contextText", english
                ? "Expressing a plan or request in everyday communication."
                : "日常交流中表达计划或请求。");
        question.put("level", request.level());
        question.put("difficulty", request.difficulty());
        question.put("grammarPoint", english ? "Basic sentence patterns and natural phrasing" : "基本句型和自然表达");
        question.put("spoken", true);
        question.put("business", false);
        question.put("exam", false);
        question.put("tagCodes", tagCodes);
        question.put("answers", buildAnswers(index));
        return question;
    }

    private String buildSourceText(int index) {
        List<String> sourceTexts = List.of(
                "我今天下午要去银行办理转账。",
                "如果明天下雨，我们就在家学习吧。",
                "请告诉我车站在哪里。",
                "我想预约明天的会议。",
                "我昨天买了一本日语书。"
        );
        return sourceTexts.get(index % sourceTexts.size());
    }

    private String buildEnglishSourceText(int index) {
        List<String> sourceTexts = List.of(
                "I need to go to the bank this afternoon to make a transfer.",
                "If it rains tomorrow, let us study at home.",
                "Please tell me where the station is.",
                "I would like to schedule tomorrow's meeting.",
                "I bought a Japanese book yesterday."
        );
        return sourceTexts.get(index % sourceTexts.size());
    }

    private List<Map<String, Object>> buildAnswers(int index) {
        List<String> answers = List.of(
                "今日の午後、銀行へ振り込みに行きます。",
                "明日雨が降ったら、家で勉強しましょう。",
                "駅はどこですか。",
                "明日の会議を予約したいです。",
                "昨日、日本語の本を買いました。"
        );

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("answerText", answers.get(index % answers.size()));
        answer.put("answerType", "STANDARD");
        answer.put("primaryAnswer", true);
        answer.put("sortOrder", 0);
        return List.of(answer);
    }
}
