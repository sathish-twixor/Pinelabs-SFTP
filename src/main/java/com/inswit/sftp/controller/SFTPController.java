package com.inswit.sftp.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inswit.sftp.service.CheckClass;
import com.inswit.sftp.service.MainReportService;



@RestController
@RequestMapping("/api")
public class SFTPController {

	private MainReportService mainReportService;	

	private CheckClass checkclass;

	@Autowired
	public SFTPController(MainReportService mainReportService,			
			CheckClass checkclass) {
		this.mainReportService = mainReportService;				
		this.checkclass = checkclass;
	}
	
	
		@Scheduled(cron = "${cron.event.expression}")
		//@GetMapping("/pinelabs_sftp")
	    public ResponseEntity<Map<String, Object>> generateReport() {
	        try {
	            Map<String, Object> result = mainReportService.fetchAndStoreData();	           
	            System.out.println("Task executed at 12:30 AM daily");
	            return ResponseEntity.ok(result);
	        } catch (IOException e) {
	            Map<String, Object> errorResponse = new HashMap<>();
	            errorResponse.put("excel_generated", false);
	            errorResponse.put("documents_downloaded", false);
	            errorResponse.put("images_downloaded", false);
	            errorResponse.put("error", "Failed to generate report: " + e.getMessage());
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
	        }
	    }
	  
//		//@Scheduled(cron = "${cron.event.expression}")
//		@GetMapping("/session-check")
//		public String sessionCheck() {
//			try {
//				checkclass.checkConnection();
//				return "connection successfully.";
//			} catch (Exception e) {
//				return "An error occured: " + e.getMessage();
//			}
//		}
	  
	  


	}