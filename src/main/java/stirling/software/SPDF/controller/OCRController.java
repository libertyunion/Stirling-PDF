package stirling.software.SPDF.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import stirling.software.SPDF.utils.ProcessExecutor;
//import com.spire.pdf.*;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
@Controller
public class OCRController {

	private static final Logger logger = LoggerFactory.getLogger(OCRController.class);

	@GetMapping("/ocr-pdf")
	public ModelAndView ocrPdfPage() {
		ModelAndView modelAndView = new ModelAndView("ocr-pdf");
		modelAndView.addObject("languages", getAvailableTesseractLanguages());
		modelAndView.addObject("currentPage", "ocr-pdf");
		return modelAndView;
	}
	
	@PostMapping("/ocr-pdf")
	public ResponseEntity<byte[]> processPdfWithOCR(@RequestParam("fileInput") MultipartFile inputFile,
			@RequestParam("languages") List<String> selectedLanguages,
			@RequestParam(name = "sidecar", required = false) Boolean sidecar,
			@RequestParam(name = "deskew", required = false) Boolean deskew,
			@RequestParam(name = "clean", required = false) Boolean clean,
			@RequestParam(name = "clean-final", required = false) Boolean cleanFinal,
			@RequestParam(name = "ocrType", required = false) String ocrType) throws IOException, InterruptedException {

	    
		//--output-type pdfa
		if (selectedLanguages == null || selectedLanguages.size() < 1) {
			throw new IOException("Please select at least one language.");
	    }
		
		// Validate and sanitize selected languages using regex
        String languagePattern = "^[a-zA-Z]{3}$"; // Regex pattern for three-letter language codes
        selectedLanguages = selectedLanguages.stream()
                .filter(lang -> Pattern.matches(languagePattern, lang))
                .collect(Collectors.toList());

        
        if (selectedLanguages.isEmpty()) {
            throw new IOException("None of the selected languages are valid.");
        }
		// Save the uploaded file to a temporary location
		Path tempInputFile = Files.createTempFile("input_", ".pdf");
		Files.copy(inputFile.getInputStream(), tempInputFile, StandardCopyOption.REPLACE_EXISTING);

		// Prepare the output file path
		Path tempOutputFile = Files.createTempFile("output_", ".pdf");

		// Prepare the output file path
        Path sidecarTextPath = null;
        
		// Run OCR Command
	    String languageOption = String.join("+", selectedLanguages);
	    
	    List<String> command = new ArrayList<>(Arrays.asList("ocrmypdf","--verbose", "2", "--output-type", "pdf"));
	    		
	    
	    if (sidecar != null && sidecar) {
	        sidecarTextPath = Files.createTempFile("sidecar", ".txt");
	        command.add("--sidecar");
	        command.add(sidecarTextPath.toString());
	    }
	    
	    if (deskew != null && deskew) {
            command.add("--deskew");
	    }
	    if (clean != null && clean) {
            command.add("--clean");
        }
	    if (cleanFinal != null && cleanFinal) {
            command.add("--clean-final");
        }
	    if (ocrType != null && !ocrType.equals("")) {
            if("skip-text".equals(ocrType)) {
                command.add("--skip-text");
            } else if("force-ocr".equals(ocrType)) {
                command.add("--force-ocr");
            } else if("Normal".equals(ocrType)) {
                
            }
        }

	    command.addAll(Arrays.asList("--language", languageOption,
	            tempInputFile.toString(), tempOutputFile.toString()));
	    
	    //Run CLI command
	    int returnCode = ProcessExecutor.getInstance(ProcessExecutor.Processes.OCR_MY_PDF).runCommandWithOutputHandling(command);
        
		// Read the OCR processed PDF file
		byte[] pdfBytes = Files.readAllBytes(tempOutputFile);
		
	    
		// Clean up the temporary files
		Files.delete(tempInputFile);
		// Return the OCR processed PDF as a response
		String outputFilename = inputFile.getOriginalFilename().replaceFirst("[.][^.]+$", "") + "_OCR.pdf";

		HttpHeaders headers = new HttpHeaders();

	    if (sidecar != null && sidecar) {
	        // Create a zip file containing both the PDF and the text file
	        String outputZipFilename = inputFile.getOriginalFilename().replaceFirst("[.][^.]+$", "") + "_OCR.zip";
	        Path tempZipFile = Files.createTempFile("output_", ".zip");

	        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(tempZipFile.toFile()))) {
	            // Add PDF file to the zip
	            ZipEntry pdfEntry = new ZipEntry(outputFilename);
	            zipOut.putNextEntry(pdfEntry);
	            Files.copy(tempOutputFile, zipOut);
	            zipOut.closeEntry();

	            // Add text file to the zip
	            ZipEntry txtEntry = new ZipEntry(outputFilename.replace(".pdf", ".txt"));
	            zipOut.putNextEntry(txtEntry);
	            Files.copy(sidecarTextPath, zipOut);
	            zipOut.closeEntry();
	        }

	        byte[] zipBytes = Files.readAllBytes(tempZipFile);

	        // Clean up the temporary zip file
	        Files.delete(tempZipFile);
	        Files.delete(tempOutputFile);
	        Files.delete(sidecarTextPath);
	        
	        // Return the zip file containing both the PDF and the text file
	        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
	        headers.setContentDispositionFormData("attachment", outputZipFilename);
	        return ResponseEntity.ok().headers(headers).body(zipBytes);
	    } else {
	        // Return the OCR processed PDF as a response
	    	Files.delete(tempOutputFile);
	        headers.setContentType(MediaType.APPLICATION_PDF);
	        headers.setContentDispositionFormData("attachment", outputFilename);
	        return ResponseEntity.ok().headers(headers).body(pdfBytes);
	    }
	    
	}

	public List<String> getAvailableTesseractLanguages() {
	    String tessdataDir = "/usr/share/tesseract-ocr/4.00/tessdata";
	    File[] files = new File(tessdataDir).listFiles();
	    if (files == null) {
	        return Collections.emptyList();
	    }
	    return Arrays.stream(files)
	            .filter(file -> file.getName().endsWith(".traineddata"))
	            .map(file -> file.getName().replace(".traineddata", ""))
	            .filter(lang -> !lang.equalsIgnoreCase("osd"))
	            .collect(Collectors.toList());
	}

}
