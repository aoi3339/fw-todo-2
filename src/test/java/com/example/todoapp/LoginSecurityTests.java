package com.example.todoapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoginSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserMapper users;
    @MockitoBean TodoService todos;

    @BeforeEach
    void account() {
        User user = new User();
        user.setUsername("login-check");
        user.setPassword(encoder.encode("test-only-password"));
        user.setRole("USER");
        when(users.countUsers()).thenReturn(1L);
        when(users.findByUsername("login-check")).thenReturn(user);
    }

    @Test
    void screensRequireLoginButLoginAndCssArePublic() throws Exception {
        for (String path : new String[]{"/", "/todos", "/todos?trash=1", "/calendar", "/todos/new"}) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/fonts/zen-maru-gothic/ZenMaruGothic-Regular.ttf"))
                .andExpect(status().isOk());
        verifyNoInteractions(todos);
    }

    @Test
    void anonymousApisReturnUnauthorizedInsteadOfLoginHtml() throws Exception {
        for (String path : new String[]{"/api/todos", "/api/todos/1", "/api/todos.csv",
                "/api/holidays", "/api/weather"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(todos);
    }

    @Test
    void validCredentialsEstablishSessionAndAllowExistingScreensAndApi() throws Exception {
        var login = mvc.perform(formLogin().user("login-check").password("test-only-password"))
                .andExpect(authenticated().withUsername("login-check"))
                .andExpect(redirectedUrl("/todos")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/todos").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/calendar").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/todos").session(session)).andExpect(status().isOk());
    }

    @Test
    void wrongCredentialsDoNotCreateAuthenticatedSession() throws Exception {
        mvc.perform(formLogin().user("login-check").password("wrong-password"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error=true"));
        mvc.perform(formLogin().user("missing-user").password("test-only-password"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void csrfPreventsUntrustedChangesAndAllowsValidFormSubmissions() throws Exception {
        mvc.perform(post("/todos/1/complete").with(user("login-check")))
                .andExpect(status().isForbidden());
        verify(todos, never()).complete(anyLong());
        mvc.perform(post("/todos/1/complete").with(user("login-check")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(todos).complete(1L);
        mvc.perform(post("/api/todos").with(user("login-check"))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesAuthenticatedSession() throws Exception {
        var result = mvc.perform(formLogin().user("login-check").password("test-only-password")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout=true")).andExpect(unauthenticated());
        assertTrue(session.isInvalid());
        mvc.perform(get("/todos")).andExpect(status().is3xxRedirection());
    }

    @Test
    void firstVisitShowsSetupAndValidSubmissionStoresOnlyBcryptHash() throws Exception {
        when(users.countUsers()).thenReturn(0L);
        mvc.perform(get("/login")).andExpect(redirectedUrl("/setup"));
        mvc.perform(get("/setup")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/setup").with(csrf()).param("username", "my-user")
                .param("password", "test-only-password").param("passwordConfirmation", "test-only-password"))
                .andExpect(redirectedUrl("/login?setup=true"));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).insertInitialUser(eq("my-user"), hash.capture());
        assertNotEquals("test-only-password", hash.getValue());
        assertTrue(encoder.matches("test-only-password", hash.getValue()));
    }

    @Test
    void setupCannotOverwriteOrCreateMoreAccountsAfterInitialization() throws Exception {
        mvc.perform(get("/setup")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/setup").with(csrf()).param("username", "other-user")
                .param("password", "test-only-password").param("passwordConfirmation", "test-only-password"))
                .andExpect(redirectedUrl("/login"));
        verify(users, never()).insertInitialUser(anyString(), anyString());
    }

    @Test
    void invalidSetupAndMissingCsrfNeverCreateAccountOrEchoPasswords() throws Exception {
        when(users.countUsers()).thenReturn(0L);
        mvc.perform(post("/setup").param("username", "new-user"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/setup").with(csrf()).param("username", "new-user")
                .param("password", "test-only-password").param("passwordConfirmation", "different-password"))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(not(containsString("test-only-password"))))
                .andExpect(content().string(not(containsString("different-password"))));
        mvc.perform(post("/setup").with(csrf()).param("username", "new-user")
                .param("password", "短".repeat(30)).param("passwordConfirmation", "短".repeat(30)))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        verify(users, never()).insertInitialUser(anyString(), anyString());
    }

    @Test
    void mcpRemainsAccessibleWithoutLoginOrCsrfAsRequested() throws Exception {
        mvc.perform(post("/mcp").header("Accept", "application/json, text/event-stream")
                .contentType("application/json")
                .content("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                    "protocolVersion":"2025-03-26","capabilities":{},
                    "clientInfo":{"name":"login-security-test","version":"1"}}}
                    """))
                .andExpect(status().isOk());
    }
}
