package com.sazare.service.impl;

import com.sazare.dto.ErrorTypeQueryRequest;
import com.sazare.entity.ErrorType;
import com.sazare.mapper.ErrorTypeMapper;
import com.sazare.vo.ErrorTypeVO;
import com.sazare.vo.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorTypeServiceImplTest {

    private ErrorTypeMapper errorTypeMapper;
    private ErrorTypeServiceImpl errorTypeService;

    @BeforeEach
    void setUp() {
        errorTypeMapper = mock(ErrorTypeMapper.class);
        errorTypeService = new ErrorTypeServiceImpl(errorTypeMapper);
    }

    @Test
    void listErrorTypesShouldUseDefaultPagination() {
        ErrorType errorType = errorType();
        when(errorTypeMapper.countErrorTypes(any())).thenReturn(1L);
        when(errorTypeMapper.selectErrorTypeList(any(), eq(20), eq(0L))).thenReturn(List.of(errorType));

        PageVO<ErrorTypeVO> page = errorTypeService.listErrorTypes(new ErrorTypeQueryRequest(
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement()
                .extracting(ErrorTypeVO::id, ErrorTypeVO::code, ErrorTypeVO::name, ErrorTypeVO::enabled)
                .containsExactly(10L, "PARTICLE", "助词错误", true);
    }

    @Test
    void listErrorTypesShouldPassFiltersAndPaginationToMapper() {
        when(errorTypeMapper.countErrorTypes(any())).thenReturn(2L);
        when(errorTypeMapper.selectErrorTypeList(any(), eq(10), eq(20L))).thenReturn(List.of());

        errorTypeService.listErrorTypes(new ErrorTypeQueryRequest(
                2,
                1L,
                true,
                3,
                10
        ));

        ArgumentCaptor<ErrorTypeQueryRequest> requestCaptor = ArgumentCaptor.forClass(ErrorTypeQueryRequest.class);
        verify(errorTypeMapper).countErrorTypes(requestCaptor.capture());
        ErrorTypeQueryRequest request = requestCaptor.getValue();
        assertThat(request.typeLevel()).isEqualTo(2);
        assertThat(request.parentId()).isEqualTo(1L);
        assertThat(request.enabled()).isTrue();
        verify(errorTypeMapper).selectErrorTypeList(any(), eq(10), eq(20L));
    }

    @Test
    void listErrorTypesShouldReturnEmptyPageWhenTotalIsZero() {
        when(errorTypeMapper.countErrorTypes(any())).thenReturn(0L);

        PageVO<ErrorTypeVO> page = errorTypeService.listErrorTypes(new ErrorTypeQueryRequest(
                null,
                null,
                false,
                2,
                50
        ));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(50);
        verify(errorTypeMapper, never()).selectErrorTypeList(any(), any(Integer.class), any(Long.class));
    }

    private ErrorType errorType() {
        ErrorType errorType = new ErrorType();
        errorType.setId(10L);
        errorType.setParentId(1L);
        errorType.setTypeLevel(2);
        errorType.setCode("PARTICLE");
        errorType.setName("助词错误");
        errorType.setDescription("助词选择、遗漏或重复");
        errorType.setSortOrder(1010);
        errorType.setEnabled(true);
        errorType.setDeleted(false);
        errorType.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        errorType.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        return errorType;
    }
}
