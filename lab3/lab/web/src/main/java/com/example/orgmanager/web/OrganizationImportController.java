package com.example.orgmanager.web;

import com.example.orgmanager.model.ImportJob;
import com.example.orgmanager.model.ImportStatus;
import com.example.orgmanager.service.OrganizationImportService;
import com.example.orgmanager.service.dto.ImportFileData;
import com.example.orgmanager.storage.ImportStorageException;
import jakarta.validation.ValidationException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/organizations/import")
public class OrganizationImportController {
    private final OrganizationImportService importService;
    private final Map<ImportStatus, String> statusLabels;

    public OrganizationImportController(OrganizationImportService importService) {
        this.importService = importService;
        this.statusLabels = buildStatusLabels();
    }

    @GetMapping
    public String importForm(Model model) {
        List<ImportJob> history = importService.history();
        model.addAttribute("history", history);
        model.addAttribute("statusLabels", statusLabels);
        return "organizations/import";
    }

    @PostMapping
    public String handleImport(@RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {
        try {
            ImportJob job = importService.importFromYaml(file);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Импорт завершен: добавлено объектов — " + job.getImportedCount());
        } catch (ValidationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Не удалось выполнить импорт: "
                            + (ex.getMessage() != null ? ex.getMessage() : "неизвестная ошибка"));
        }
        return "redirect:/organizations/import";
    }

    @GetMapping("/{jobId}/file")
    public ResponseEntity<StreamingResponseBody> downloadImportFile(
            @PathVariable("jobId") Long jobId) {
        ImportFileData fileData;
        try {
            fileData = importService.openImportFile(jobId);
        } catch (ValidationException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ImportStorageException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ex.getMessage(),
                    ex);
        }
        StreamingResponseBody body = outputStream -> {
            try (fileData) {
                fileData.stream().transferTo(outputStream);
            }
        };
        String encodedName = UriUtils.encodePathSegment(fileData.fileName(), java.nio.charset.StandardCharsets.UTF_8);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s\"".formatted(encodedName));
        if (fileData.contentType() != null) {
            response.contentType(MediaType.parseMediaType(fileData.contentType()));
        }
        if (fileData.fileSize() != null) {
            response.contentLength(fileData.fileSize());
        }
        return response.body(body);
    }

    private Map<ImportStatus, String> buildStatusLabels() {
        Map<ImportStatus, String> labels = new EnumMap<>(ImportStatus.class);
        labels.put(ImportStatus.IN_PROGRESS, "В обработке");
        labels.put(ImportStatus.SUCCESS, "Успех");
        labels.put(ImportStatus.FAILED, "Ошибка");
        return labels;
    }
}
