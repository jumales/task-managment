package com.demo.taskmanager.feature.users.list

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.repo.UserRepository

/**
 * Offset-based [PagingSource] for the users list.
 * Each page key is the zero-based page index; [PageDto.last] signals the final page.
 */
class UsersPagingSource(
    private val repository: UserRepository,
) : PagingSource<Int, UserDto>() {

    override fun getRefreshKey(state: PagingState<Int, UserDto>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UserDto> {
        val page = params.key ?: 0
        return when (val result = repository.getUsers(page = page, size = params.loadSize)) {
            is NetworkResult.Success -> LoadResult.Page(
                data = result.data.content,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (result.data.last) null else page + 1,
            )
            is NetworkResult.Error -> LoadResult.Error(Exception("HTTP ${result.code}"))
            is NetworkResult.Exception -> LoadResult.Error(result.throwable)
        }
    }
}
