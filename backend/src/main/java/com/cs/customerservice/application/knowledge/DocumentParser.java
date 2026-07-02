package com.cs.customerservice.application.knowledge;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    public ParseResult parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "unknown";

        String lower = filename.toLowerCase();
        String text;
        int totalPages = 0;

        if (lower.endsWith(".pdf")) {
            try (InputStream in = file.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    text = stripper.getText(doc);
                    totalPages = doc.getNumberOfPages();
                }
            }
        } else if (lower.endsWith(".docx")) {
            try (InputStream in = file.getInputStream();
                 XWPFDocument doc = new XWPFDocument(in)) {
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
                text = extractor.getText();
            }
        } else {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        log.info("Parsed: file={}, len={}, pages={}", filename, text.length(), totalPages);
        return new ParseResult(text, filename, totalPages);
    }

    public record ParseResult(String text, String sourceName, int totalPages) {}
}
