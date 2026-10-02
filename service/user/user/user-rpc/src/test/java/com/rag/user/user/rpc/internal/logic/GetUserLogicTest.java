package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.GetUserReq;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.User;
import com.rag.user.user.rpc.internal.model.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 对应 Go: internal/logic/getuser_status_test.go
 *
 * Go 端的 getuser_status_test.go 用 73 行测试 GetUserLogic 的三种 Found 状态。
 * Java 端用 JUnit 5 + Mockito,1:1 复刻核心断言:
 *   - Found=true 时 UserInfo 字段映射正确
 *   - Found=false / 用户不存在时抛 BusinessException(USER_NOT_FOUND)
 *
 * 集成测试(Testcontainers 起 Postgres)留给后续模块,这里只做 Logic 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetUserLogic 单元测试")
class GetUserLogicTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetUserLogic getUserLogic;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setUid(1001L);
        sampleUser.setUsername("alice");
        sampleUser.setEmail("alice@example.com");
        sampleUser.setStatus(0L);
        sampleUser.setScore(10);

        Map<String, String> extra = new HashMap<>();
        extra.put("avatar", "https://cdn.example.com/avatars/1001.png");
        sampleUser.setExtraInfo(extra);
    }

    @Test
    @DisplayName("Found=true:UserInfo 字段一一映射")
    void getUser_whenUserExists_returnsFoundTrueWithUserInfo() {
        // Given
        when(userRepository.findByUid(1001L)).thenReturn(Optional.of(sampleUser));

        // When
        var resp = getUserLogic.getUser(GetUserReq.newBuilder().setUid(1001L).build());

        // Then
        assertThat(resp.getFound()).isTrue();
        assertThat(resp.getUser().getUid()).isEqualTo(1001L);
        assertThat(resp.getUser().getUsername()).isEqualTo("alice");
        assertThat(resp.getUser().getEmail()).isEqualTo("alice@example.com");
        assertThat(resp.getUser().getScore()).isEqualTo(10);
        assertThat(resp.getUser().getStatus()).isEqualTo(0L);  // 1:1 复刻 Go 的 Status: &user.Status
        assertThat(resp.getUser().getExtraInfoMap())
                .containsEntry("avatar", "https://cdn.example.com/avatars/1001.png");
    }

    @Test
    @DisplayName("用户不存在:抛 BusinessException(USER_NOT_FOUND)")
    void getUser_whenUserNotFound_throwsUserNotFound() {
        // Given
        when(userRepository.findByUid(9999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() ->
                getUserLogic.getUser(GetUserReq.newBuilder().setUid(9999L).build())
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }
}