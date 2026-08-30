package com.sazare.service;

import com.sazare.entity.Question;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.QuestionTagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewQuestionTagWriter {

    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;

    public ReviewQuestionTagWriter(QuestionMapper questionMapper, QuestionTagMapper questionTagMapper) {
        this.questionMapper = questionMapper;
        this.questionTagMapper = questionTagMapper;
    }

    @Transactional
    public boolean saveIfUntagged(Long questionId, List<Long> tagIds) {
        Question question = questionMapper.selectQuestionForUpdateById(questionId);
        if (question == null || questionTagMapper.countByQuestionId(questionId) > 0) {
            return false;
        }
        for (Long tagId : tagIds.stream().distinct().toList()) {
            questionTagMapper.insertQuestionTag(questionId, tagId);
        }
        return true;
    }
}
