package com.middleware.leave_approval_system.Controller;

import com.middleware.leave_approval_system.Entity.LeaveRequest;
import com.middleware.leave_approval_system.Exception.ResourceNotFoundException;
import com.middleware.leave_approval_system.Service.LeaveRequestServiceImpl;
import com.middleware.leave_approval_system.Util.HolidaysUtil;
import com.azure.storage.blob.BlobClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/leave")
public class LeaveRequestController {

    @Autowired
    private LeaveRequestServiceImpl leaveRequestServiceImpl;

    @Value("${azure.storage.connection-string}")
    private String azureConnectionString;

    @Value("${azure.storage.container-name}")
    private String azureContainerName;

    @PostMapping("/submit")
    public ResponseEntity<?> submitLeaveRequest(
            @RequestParam("employeeId") String employeeId,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam("position") String position,
            @RequestParam("phone") String phone,
            @RequestParam("managerId") String managerId,
            @RequestParam("managerName") String managerName,
            @RequestParam("managerEmail") String managerEmail,
            @RequestParam(value = "comments", required = false) String comments,
            @RequestParam(value="durationType", required = false) String durationType,
            @RequestParam(value = "duration", required = false) Double duration,
            @RequestParam("leaveStartDate") LocalDate leaveStartDate,
            @RequestParam("leaveEndDate") LocalDate leaveEndDate,
            @RequestParam(value = "leaveReason", required = false) String leaveReason,
            @RequestParam(value = "leaveStatus", required = false) LeaveRequest.LeaveStatus leaveStatus,
            @RequestParam(value = "leaveType", required = false) LeaveRequest.LeaveType leaveType,
            @RequestParam(value = "medicalDocument", required = false) MultipartFile medicalDocument) throws IOException {
        try {
            LeaveRequest leaveRequest = new LeaveRequest();
            leaveRequest.setEmployeeId(employeeId);
            leaveRequest.setFirstName(firstName);
            leaveRequest.setLastName(lastName);
            leaveRequest.setEmail(email);
            leaveRequest.setPosition(position);
            leaveRequest.setPhone(phone);
            leaveRequest.setManagerId(managerId);
            leaveRequest.setManagerName(managerName);
            leaveRequest.setManagerEmail(managerEmail);
            leaveRequest.setComments(comments);
            leaveRequest.setLeaveType(leaveType);
            leaveRequest.setLeaveStartDate(leaveStartDate);
            leaveRequest.setLeaveEndDate(leaveEndDate);
            leaveRequest.setLeaveReason(leaveReason);
            leaveRequest.setLeaveStatus(leaveStatus);

            if (leaveType == LeaveRequest.LeaveType.SICK) {
                double requestedDays = leaveRequest.calculateBusinessDays(
                        leaveStartDate, leaveEndDate, HolidaysUtil.getNationalHolidays(leaveStartDate.getYear())
                );
                if (requestedDays > 2 && medicalDocument != null) {
                    String fileUrl = uploadFileToBlobStorage(medicalDocument, "medicalDocument");
                    leaveRequest.setMedicalDocument(fileUrl);
                }
            }

            LeaveRequest savedRequest = leaveRequestServiceImpl.submitLeaveRequest(leaveRequest);
            return new ResponseEntity<>(savedRequest, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>("File upload failed: " + e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + e.getMessage());
        }
    }

    private String uploadFileToBlobStorage(MultipartFile file, String fileType) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or null");
        }

        // Generate a unique file name
        String uniqueFileName = fileType + "-" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        // Upload to Azure Blob Storage
        new BlobClientBuilder()
                .connectionString(azureConnectionString)
                .containerName(azureContainerName)
                .blobName(uniqueFileName)
                .buildClient()
                .upload(file.getInputStream(), file.getSize(), true);

        // Return the file URL
        return String.format("https://<your-storage-account>.blob.core.windows.net/%s/%s", azureContainerName, uniqueFileName);
    }

    // Approving a leave request
    @PutMapping("/approve/{id}")
    public ResponseEntity<String> approveLeaveRequest(@PathVariable Long id) {
        leaveRequestServiceImpl.approveLeaveRequest(id);
        return ResponseEntity.ok("Leave Request Approved");
    }

    // Rejecting a leave request
    @PutMapping("/reject/{id}/{leaveReason}")
    public ResponseEntity<String> rejectLeaveRequest(@PathVariable Long id, @PathVariable String leaveReason) {
        leaveRequestServiceImpl.rejectLeaveRequest(id, leaveReason);
        return ResponseEntity.ok("Leave Request Rejected with Reason: " + leaveReason);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<LeaveRequest> updateLeaveRequest(@PathVariable Long id, @RequestBody LeaveRequest leaveRequest) {
        LeaveRequest updatedLeaveRequest = leaveRequestServiceImpl.updateLeaveRequest(id, leaveRequest);
        return ResponseEntity.ok(updatedLeaveRequest);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteLeaveRequest(@PathVariable Long id) {
        String deleteRequest = leaveRequestServiceImpl.deleteLeaveRequest(id);
        return ResponseEntity.ok(deleteRequest);
    }

    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAllLeaveRequests() {
        List<LeaveRequest> leaveRequest = leaveRequestServiceImpl.getAllLeaveRequests();
        if (leaveRequest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(leaveRequest);
        }
        return new ResponseEntity<>(leaveRequest, HttpStatus.OK);
    }

    @GetMapping("/{status}/manager/{managerId}")
    public ResponseEntity<List<LeaveRequest>> getLeaveRequestsByStatus(@PathVariable String status, @PathVariable String managerId) {
        LeaveRequest.LeaveStatus leaveStatus;
        try {
            leaveStatus = LeaveRequest.LeaveStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        List<LeaveRequest> leaveRequests = leaveRequestServiceImpl.getLeaveRequestsByStatus(managerId, leaveStatus);
        if (leaveRequests.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(leaveRequests);
        }
        return ResponseEntity.ok(leaveRequests);
    }

    @GetMapping("/pending/employee/{employeeId}")
    public ResponseEntity<List<LeaveRequest>> getAllPendingLeaveRequestsEmployees(@PathVariable String employeeId) {
        List<LeaveRequest> pendingRequests = leaveRequestServiceImpl.getAllPendingLeaveRequestsEmployee(employeeId);
        if (pendingRequests.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pendingRequests);
        }
        return new ResponseEntity<>(pendingRequests, HttpStatus.OK);
    }

    @GetMapping("/approve/employee/{employeeId}")
    public ResponseEntity<List<LeaveRequest>> getAllApprovedLeaveRequestsEmployee(@PathVariable String employeeId) {
        List<LeaveRequest> approveRequests = leaveRequestServiceImpl.getAllApprovedLeaveRequestsEmployee(employeeId);
        if (approveRequests.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(approveRequests);
        }
        return new ResponseEntity<>(approveRequests, HttpStatus.OK);
    }

    @GetMapping("/reject/employee/{employeeId}")
    public ResponseEntity<List<LeaveRequest>> getAllRejectedLeaveRequestsEmployee(@PathVariable String employeeId) {
        List<LeaveRequest> rejectRequests = leaveRequestServiceImpl.getAllRejectedLeaveRequestsEmployee(employeeId);
        if (rejectRequests.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(rejectRequests);
        }
        return new ResponseEntity<>(rejectRequests, HttpStatus.OK);
    }

    @GetMapping("/manager/{managerId}")
    public ResponseEntity<List<LeaveRequest>> getAllMangerIds(@PathVariable String managerId) {
        List<LeaveRequest> leaveRequest = leaveRequestServiceImpl.getAllManagerId(managerId);
        if (leaveRequest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(leaveRequest);
        }
        return new ResponseEntity<>(leaveRequest, HttpStatus.OK);
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveRequest>> getAllEmployeeIds(@PathVariable String employeeId) {
        List<LeaveRequest> leaveRequest = leaveRequestServiceImpl.getAllEmployeeId(employeeId);
        return ResponseEntity.ok(leaveRequest);
    }

    @GetMapping("/fileSize")
    public ResponseEntity<Map<String, Long>> getFileSize(@RequestParam String fileName) {
        try {
            // File size retrieval is not relevant for Azure Blob Storage; update functionality as needed
            Map<String, Long> response = new HashMap<>();
            response.put("size", 0L);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Long> response = new HashMap<>();
            response.put("size", 0L);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}