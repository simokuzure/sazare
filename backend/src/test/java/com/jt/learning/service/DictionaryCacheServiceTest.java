package com.jt.learning.service;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.entity.Tag;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class DictionaryCacheServiceTest {

    private static final String ERROR_TYPE_CACHE_KEY = "dictionary:error-types:enabled-leaf:v1";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TagMapper tagMapper;
    private ErrorTypeMapper errorTypeMapper;
    private DictionaryCacheService dictionaryCacheService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        tagMapper = mock(TagMapper.class);
        errorTypeMapper = mock(ErrorTypeMapper.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        dictionaryCacheService = new DictionaryCacheService(
                stringRedisTemplate, new ObjectMapper(), tagMapper, errorTypeMapper, CACHE_TTL);
    }

    @Test
    void shouldLoadErrorTypesFromDatabaseThenReadTheCachedValue() {
        List<AiErrorTypeOptionDTO> errorTypes = List.of(errorType("PARTICLE"));
        when(errorTypeMapper.selectEnabledLeafOptions()).thenReturn(errorTypes);

        List<AiErrorTypeOptionDTO> first = dictionaryCacheService.getEnabledLeafErrorTypes();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(ERROR_TYPE_CACHE_KEY), valueCaptor.capture(), eq(CACHE_TTL));
        when(valueOperations.get(ERROR_TYPE_CACHE_KEY)).thenReturn(valueCaptor.getValue());

        List<AiErrorTypeOptionDTO> second = dictionaryCacheService.getEnabledLeafErrorTypes();

        assertThat(first).containsExactlyElementsOf(errorTypes);
        assertThat(second).containsExactlyElementsOf(errorTypes);
        verify(errorTypeMapper, times(1)).selectEnabledLeafOptions();
    }

    @Test
    void shouldCacheAnEmptyTagList() {
        when(tagMapper.selectEnabledTagsByType("SCENE")).thenReturn(List.of());

        List<Tag> tags = dictionaryCacheService.getEnabledTagsByType("SCENE");

        assertThat(tags).isEmpty();
        verify(valueOperations).set(
                eq("dictionary:tags:SCENE:enabled:v1"), eq("[]"), eq(CACHE_TTL));
    }

    @Test
    void shouldReadCachedTagsWithoutCallingTheMapper() {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setTagType("FUNCTION");
        tag.setCode("FUNCTION_REQUEST");
        tag.setName("请求");
        when(tagMapper.selectEnabledTagsByType("FUNCTION")).thenReturn(List.of(tag));

        dictionaryCacheService.getEnabledTagsByType("FUNCTION");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("dictionary:tags:FUNCTION:enabled:v1"), valueCaptor.capture(), eq(CACHE_TTL));
        when(valueOperations.get("dictionary:tags:FUNCTION:enabled:v1")).thenReturn(valueCaptor.getValue());

        List<Tag> cached = dictionaryCacheService.getEnabledTagsByType("FUNCTION");

        assertThat(cached).extracting(Tag::getCode).containsExactly("FUNCTION_REQUEST");
        verify(tagMapper, times(1)).selectEnabledTagsByType("FUNCTION");
    }

    @Test
    void shouldDeleteMalformedCachedValueAndReloadFromDatabase() {
        when(valueOperations.get(ERROR_TYPE_CACHE_KEY)).thenReturn("not-json");
        when(errorTypeMapper.selectEnabledLeafOptions()).thenReturn(List.of(errorType("WORD_ORDER")));

        List<AiErrorTypeOptionDTO> result = dictionaryCacheService.getEnabledLeafErrorTypes();

        assertThat(result).extracting(AiErrorTypeOptionDTO::code).containsExactly("WORD_ORDER");
        verify(stringRedisTemplate).delete(ERROR_TYPE_CACHE_KEY);
        verify(errorTypeMapper).selectEnabledLeafOptions();
    }

    @Test
    void shouldFallBackToDatabaseWhenRedisIsUnavailable() {
        when(valueOperations.get(ERROR_TYPE_CACHE_KEY)).thenThrow(new IllegalStateException("Redis 不可用"));
        doThrow(new IllegalStateException("Redis 不可用"))
                .when(valueOperations).set(eq(ERROR_TYPE_CACHE_KEY), anyString(), eq(CACHE_TTL));
        when(errorTypeMapper.selectEnabledLeafOptions()).thenReturn(List.of(errorType("PARTICLE")));

        List<AiErrorTypeOptionDTO> result = dictionaryCacheService.getEnabledLeafErrorTypes();

        assertThat(result).extracting(AiErrorTypeOptionDTO::code).containsExactly("PARTICLE");
        verify(errorTypeMapper).selectEnabledLeafOptions();
    }

    private AiErrorTypeOptionDTO errorType(String code) {
        return new AiErrorTypeOptionDTO(1L, code, "错误类型", "说明", "GRAMMAR", "语法");
    }
}
