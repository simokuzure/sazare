package com.jt.learning.service.impl;

import com.jt.learning.entity.Tag;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.service.TagService;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.TagVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TagServiceImpl implements TagService {

    private static final Set<String> VALID_TAG_TYPES = Set.of("SCENE", "FUNCTION", "GENRE");
    private static final int MAX_PAGE_SIZE = 100;

    private final TagMapper tagMapper;

    public TagServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public PageVO<TagVO> listTags(String tagType, Long parentId, boolean enabledOnly, int page, int size) {
        String normalizedTagType = normalizeTagType(tagType);
        validatePage(page, size);

        long offset = (long) (page - 1) * size;
        long total = tagMapper.countTags(normalizedTagType, parentId, enabledOnly);
        List<TagVO> items = tagMapper.selectTags(normalizedTagType, parentId, enabledOnly, size, offset)
                .stream()
                .map(this::toVO)
                .toList();

        return new PageVO<>(items, page, size, total);
    }

    private String normalizeTagType(String tagType) {
        if (tagType == null) {
            return null;
        }

        String normalizedTagType = tagType.trim();
        if (!VALID_TAG_TYPES.contains(normalizedTagType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tagType 不合法");
        }
        return normalizedTagType;
    }

    private void validatePage(int page, int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page 必须大于等于 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "size 必须在 1 到 100 之间");
        }
    }

    private TagVO toVO(Tag tag) {
        return new TagVO(
                tag.getId(),
                tag.getTagType(),
                tag.getParentId(),
                tag.getCode(),
                tag.getName(),
                tag.getDescription(),
                tag.getSortOrder()
        );
    }
}
