package com.newsplore.inception.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static com.newsplore.DocumentationConfig.API_HOST;
import static com.newsplore.DocumentationConfig.GENERATED_SNIPPETS_DIR;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.fileUpload;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureRestDocs(value = GENERATED_SNIPPETS_DIR, uriHost = API_HOST, uriPort = 80)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest
public class AppControllerTest {
    @Autowired private MockMvc mockMvc;

    @Autowired private ResourceLoader resourceLoader;

    @Test
    public void classifyImageOk() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(resourceLoader.getResource("classpath:boat.jpg").getURI()));    
        MockMultipartFile file = new MockMultipartFile("file", "boat.jpg", "image/jpeg", bytes);
        String blowfish = this.mockMvc.perform(
            fileUpload("/api/classify")
                .file(file))
            .andDo(document("classify-image-ok/{step}",
                preprocessRequest(prettyPrint(), replacePattern(Pattern.compile(".*"), "...boat.jpg multipart binary contents...")),
                preprocessResponse(prettyPrint())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("label", notNullValue()))
            .andExpect(jsonPath("probability", notNullValue()))
            .andReturn().getResponse().getContentAsString();
    }
}