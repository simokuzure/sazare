package com.sazare.vo;

import java.util.List;

public record ChatImportConfirmationVO(List<CardResult> cards) {
    public record CardResult(Long cardId, Long userErrorTypeId, List<Long> questionIds, String action) { }
}
