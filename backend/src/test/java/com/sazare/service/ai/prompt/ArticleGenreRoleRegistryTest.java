package com.sazare.service.ai.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleGenreRoleRegistryTest {

    @Test
    void shouldProvideExactRoleKeysForEveryGenre() {
        assertRoleKeys("NARRATIVE", "subject", "setting", "experience", "changeOrInsight");
        assertRoleKeys("EXPOSITORY", "subject", "angle", "detail", "impactOrComparison");
        assertRoleKeys("OPINION", "issue", "position", "reason", "counterpointOrConstraint");
        assertRoleKeys("PRACTICAL", "purpose", "senderAndRecipient", "situation", "requiredInformationOrAction");
        assertRoleKeys("ESSAY", "triggerImage", "feeling", "association", "reflection");
        assertRoleKeys("DIARY", "timeContext", "dailyExperience", "emotionalChange", "unresolvedThought");
        assertRoleKeys("DIALOGUE", "participantRelationship", "setting", "informationGapOrDisagreement", "communicationGoal");
        assertRoleKeys("NEWS_REPORT", "event", "timeAndPlace", "affectedParty", "causeImpactOrResponse");
        assertRoleKeys("INTERVIEW", "interviewee", "topic", "keyExperienceOrView", "followUpDirection");
        assertRoleKeys("REVIEW", "reviewSubject", "usageScenario", "criteria", "tradeoff");
        assertRoleKeys("GUIDE", "audience", "goal", "prerequisiteOrConstraint", "commonMistakeOrTip");
        assertRoleKeys("FICTION", "protagonistOrEntity", "fictionalSetting", "specialObjectOrRule", "desireOrConflict");
    }

    private void assertRoleKeys(String genreCode, String... roleKeys) {
        assertThat(ArticleGenreRoleRegistry.roleKeysFor(genreCode)).containsExactly(roleKeys);
    }
}
