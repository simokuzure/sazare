package com.jt.learning.service;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.entity.Tag;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.TagMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class DictionaryCacheService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryCacheService.class);
    private static final String ERROR_TYPE_CACHE_KEY = "dictionary:error-types:enabled-leaf:v1";
    private static final String TAG_CACHE_KEY_PREFIX = "dictionary:tags:";
    private static final String TAG_CACHE_KEY_SUFFIX = ":enabled:v1";
    private static final Set<String> CACHEABLE_TAG_TYPES = Set.of("SCENE", "FUNCTION", "GENRE");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TagMapper tagMapper;
    private final ErrorTypeMapper errorTypeMapper;
    private final Duration ttl;

    public DictionaryCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            TagMapper tagMapper,
            ErrorTypeMapper errorTypeMapper,
            @Value("${dictionary-cache.ttl:24h}") Duration ttl
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.tagMapper = tagMapper;
        this.errorTypeMapper = errorTypeMapper;
        this.ttl = ttl;
    }

    public List<AiErrorTypeOptionDTO> getEnabledLeafErrorTypes() {
        List<AiErrorTypeOptionDTO> cached = readErrorTypes();
        if (cached != null) {
            return cached;
        }

        List<AiErrorTypeOptionDTO> errorTypes = immutable(errorTypeMapper.selectEnabledLeafOptions());
        write(ERROR_TYPE_CACHE_KEY, errorTypes);
        return errorTypes;
    }

    public List<Tag> getEnabledTagsByType(String tagType) {
        if (!CACHEABLE_TAG_TYPES.contains(tagType)) {
            return immutable(tagMapper.selectEnabledTagsByType(tagType));
        }

        String cacheKey = tagCacheKey(tagType);
        List<Tag> cached = readTags(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Tag> tags = immutable(tagMapper.selectEnabledTagsByType(tagType));
        write(cacheKey, tags.stream().map(CachedTag::from).toList());
        return tags;
    }

    private List<AiErrorTypeOptionDTO> readErrorTypes() {
        String value = read(ERROR_TYPE_CACHE_KEY);
        if (value == null) {
            return null;
        }
        try {
            return immutable(objectMapper.readValue(value, listType(AiErrorTypeOptionDTO.class)));
        } catch (JacksonException exception) {
            log.warn("错误类型字典缓存解析失败，改为查询数据库: {}", exception.toString());
            delete(ERROR_TYPE_CACHE_KEY);
            return null;
        }
    }

    private List<Tag> readTags(String cacheKey) {
        String value = read(cacheKey);
        if (value == null) {
            return null;
        }
        try {
            List<CachedTag> cachedTags = objectMapper.readValue(value, listType(CachedTag.class));
            return cachedTags.stream()
                    .map(CachedTag::toTag)
                    .toList();
        } catch (JacksonException exception) {
            log.warn("标签字典缓存解析失败，改为查询数据库: {}", exception.toString());
            delete(cacheKey);
            return null;
        }
    }

    private String read(String cacheKey) {
        try {
            return stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException exception) {
            log.warn("读取 Redis 字典缓存失败，改为查询数据库，key={}, reason={}", cacheKey, exception.toString());
            return null;
        }
    }

    private void write(String cacheKey, Object value) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException exception) {
            log.warn("写入 Redis 字典缓存失败，后续请求将查询数据库，key={}, reason={}", cacheKey, exception.toString());
        }
    }

    private void delete(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            log.warn("删除无效 Redis 字典缓存失败，key={}, reason={}", cacheKey, exception.toString());
        }
    }

    private TypeFactory typeFactory() {
        return objectMapper.getTypeFactory();
    }

    private <T> tools.jackson.databind.JavaType listType(Class<T> elementType) {
        return typeFactory().constructCollectionType(List.class, elementType);
    }

    private <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String tagCacheKey(String tagType) {
        return TAG_CACHE_KEY_PREFIX + tagType + TAG_CACHE_KEY_SUFFIX;
    }

    private record CachedTag(
            Long id,
            String tagType,
            Long parentId,
            String code,
            String name,
            String description,
            Integer sortOrder,
            Boolean enabled,
            Boolean deleted
    ) {
        private static CachedTag from(Tag tag) {
            return new CachedTag(
                    tag.getId(), tag.getTagType(), tag.getParentId(), tag.getCode(), tag.getName(),
                    tag.getDescription(), tag.getSortOrder(), tag.getEnabled(), tag.getDeleted());
        }

        private Tag toTag() {
            Tag tag = new Tag();
            tag.setId(id);
            tag.setTagType(tagType);
            tag.setParentId(parentId);
            tag.setCode(code);
            tag.setName(name);
            tag.setDescription(description);
            tag.setSortOrder(sortOrder);
            tag.setEnabled(enabled);
            tag.setDeleted(deleted);
            return tag;
        }
    }
}
