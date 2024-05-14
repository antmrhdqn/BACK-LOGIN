package com.insider.login.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insider.login.auth.model.dto.LoginDTO;
import com.insider.login.department.service.DepartmentService;
import com.insider.login.member.dto.MemberDTO;
import com.insider.login.member.dto.UpdatePasswordRequestDTO;
import com.insider.login.member.entity.Member;
import com.insider.login.member.service.MemberService;
import com.insider.login.position.service.PositionService;
import com.insider.login.transferredHistory.service.TransferredHistoryService;
import org.apache.coyote.Response;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.*;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.insider.login.common.utils.TokenUtils.getTokenInfo;

@RestController
@RequestMapping
@CrossOrigin(origins = "http://localhost:3000")
public class MemberController {

    @Value("${jwt.key}")
    private String jwtSecret;
    private final MemberService memberService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TransferredHistoryService transferredHistoryService;
    private final PositionService positionService;
    private final DepartmentService departmentService;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoders;

    public MemberController(MemberService memberService, BCryptPasswordEncoder passwordEncoder, TransferredHistoryService transferredHistoryService, PositionService positionService, DepartmentService departmentService, ModelMapper modelMapper, PasswordEncoder passwordEncoders) {
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.transferredHistoryService = transferredHistoryService;
        this.positionService = positionService;
        this.departmentService = departmentService;
        this.modelMapper = modelMapper;
        this.passwordEncoders = passwordEncoders;
    }

    /** 구성원 등록 */
    @PostMapping("/signUp")
    public String signUp(@RequestBody MemberDTO memberDTO) {

        // 2자릿수 년도
        String twoDigitYear = Year.now().format(DateTimeFormatter.ofPattern("yy"));
        System.out.println("2자릿수 년도: " + twoDigitYear);

        // 2자릿수 월
        String twoDigitMonth = String.format("%02d", YearMonth.now().getMonthValue());
        System.out.println("2자릿수 월: " + twoDigitMonth);

        // 2자릿수 부서번호
        String departNo = String.valueOf(memberDTO.getDepartmentDTO().getDepartNo());
        if (departNo.length() == 1) {
            departNo = "0" + departNo;
        }

        // unique memberId 생성 (중복 X)
        int setMemberId;
        boolean existingId;
        do {
            Random random = new Random();
            int randomNumber = random.nextInt(900) + 100;
            setMemberId = Integer.parseInt(twoDigitYear + twoDigitMonth + departNo + randomNumber);

            existingId = memberService.findExistingMemberId(setMemberId);
        } while (existingId);

        memberDTO.setMemberId(setMemberId);
        memberDTO.setPassword("0000");
        String encodedPassword = passwordEncoder.encode(memberDTO.getPassword());
        memberDTO.setPassword(encodedPassword);
        memberDTO.setMemberStatus("재직");

        // JSON형식으로 LocalDate을 저장을 하기 위한 logic
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        LocalDate localDate = LocalDate.now();
        memberDTO.setEmployedDate(localDate);               // 입사일

        Member savedMember = memberService.saveMember(memberDTO);
        System.out.println("회원 가입한 구성원 정보: " + savedMember);

        // 회원가입을 하면 최초로 구성원의 인사발령 내역을 저장을 해야하기 때문에 작성하는 코드
        transferredHistoryService.saveHistory(savedMember);

        if(Objects.isNull(savedMember)) { // 비어있으면 실패
            System.out.println("회원가입 실패 🥲");
            return "회원가입 실패";
        } else {                    // 다 작성을 했으면 구성원 가입 성공
            System.out.println("회원가입 성공 🙂");
            return "회원 가입 성공!";
        }
    }


    /** 특정 구성원 정보 조회 */
    @GetMapping("/members/{memberId}")
    public String getSpecificMemberById(@PathVariable("memberId") int memberId) {
        System.out.println("받은 memberId: " + memberId);
        MemberDTO foundMember = memberService.findSpecificMember(memberId);
        System.out.println("특정 구성원 정보 조회: " + foundMember);

        if (foundMember != null) {
            return "foundMember: " + foundMember;
        } else {
            return "memberNotFound";
        }
    }

    /** 구성원 정보 수정 */
    @PutMapping("/members/updateProfile/{memberId}")
    public String updateSpecificMemberById(@PathVariable("memberId") int memberId, @RequestBody MemberDTO inputtedMemberInfo) {
        /* 특정 구성원의 정보를 전부 가져온다 */
        MemberDTO specificMember = memberService.findSpecificMember(memberId);
        System.out.println("specificMember: " + specificMember); // 확인용

        inputtedMemberInfo.setMemberId(memberId);
        inputtedMemberInfo.setPassword(specificMember.getPassword());
        inputtedMemberInfo.setEmployedDate(specificMember.getEmployedDate());
        /*
        inputtedMemberInfo.setAddress(specificMember.getAddress());
        inputtedMemberInfo.setRole(specificMember.getRole());
        inputtedMemberInfo.setImageUrl(specificMember.getImageUrl());
        */
        System.out.println("수정을 하기 전 구성원의 정보: " + specificMember);
        System.out.println("입력 받은 값: " + inputtedMemberInfo);

        /* 입력 받은 것을 덮어 쓴다 */
        String result = memberService.updateMember(inputtedMemberInfo);
        System.out.println("updated member info: " + result);

        /* 퇴직으로 바뀌면 바뀐 시점으로부터 3년 뒤에 삭제 */
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        LocalDate currentDate = LocalDate.now();

        LocalDate deleteDate = currentDate.plus(3, ChronoUnit.YEARS);
        long delay = ChronoUnit.DAYS.between(currentDate, deleteDate);

        executorService.schedule(() -> {
            memberService.deleteMemberById(memberId);
            System.out.println("Member (" + memberId + ") will be deleted 3 years from now" );
        }, delay, TimeUnit.DAYS);

        executorService.shutdown();

        return "updated member info: " + result;
    }

    /** 구성원 본인 비밀번호 */
    @PostMapping("/updateOwnPassword")
    public String updateOwnPassword(@RequestBody UpdatePasswordRequestDTO updatePasswordRequestDTO) {

        MemberDTO foundMember = memberService.findPasswordByMemberId(getTokenInfo().getMemberId());
        String existingPassword = foundMember.getPassword();
        System.out.println("기존에 있는 비밀번호: " + existingPassword);

        /* 입력한 현재 비밀번호가 일치하는지 확인하는 logic */
        if (!passwordEncoder.matches(updatePasswordRequestDTO.getCurrentPassword(), existingPassword)) {
            System.out.println("비밀번호가 틀렸습니다");
            return "wrong password";
        } else if (!updatePasswordRequestDTO.getNewPassword1().equals(updatePasswordRequestDTO.getNewPassword2())) {
            System.out.println("비밀번호가 일치하지 않습니다.");
            return "password doesn't match";
        } else {
            String hashedNewPassword = passwordEncoder.encode(updatePasswordRequestDTO.getNewPassword2());
            String result = memberService.changePassword(hashedNewPassword, getTokenInfo123().getMemberId());
            return "successfully changed the password" + result;
        }
    }

    /** Claims에 구성원 정보를 받아오는 방법 */
    /**
     * getTokenInfo()에서 받아올 수 있는 정보들
     * 1. memberID -> getMemberId
     * 2. name -> getName
     * 3. memberStatus -> getMemberStatus
     * 4. role -> getRole.getRole()
     * 5. departmentName ->getDepartmentDTO.getDepartName
     * 6. positionName ->getPositionDTO.getPositionName
     * 6. imageUrl -> getImageUrl
     * */
    @GetMapping("/getTokenInfo")
    public MemberDTO getTokenInfo123() {
        System.out.println("회원의 비밀번호: " + getTokenInfo().getMemberId());
        System.out.println("memberId: " + getTokenInfo().getMemberId());
        System.out.println("name: " + getTokenInfo().getName());
        System.out.println("member status: " + getTokenInfo().getMemberStatus());
        System.out.println("role: " + getTokenInfo().getRole().getRole());
        System.out.println("positionName: " + getTokenInfo().getDepartmentDTO().getDepartName());
        System.out.println("department name: " + getTokenInfo().getPositionDTO().getPositionName());
        System.out.println("image url: " + getTokenInfo().getImageUrl());
        return getTokenInfo();
    }

    /** 구성원 전체 조회 */
    @GetMapping("/showAllMembersPage")
    public List<MemberDTO> showAllMembersPage() {
        System.out.println("show all member infos in the page");
        List<MemberDTO> memberLists = memberService.showAllMembers();
        System.out.println("memberList: " + memberLists);

        for (MemberDTO member : memberLists) {
            System.out.println("memberName: " + member.getName());
            System.out.println("memberId: " + member.getMemberId());
            System.out.println("member department name: " + member.getDepartmentDTO().getDepartName());
            System.out.println("member position name: " + member.getPositionDTO().getPositionName());
            System.out.println("employedDate: " + member.getEmployedDate());

            /* 근속년수 */
            LocalDate employedDate = member.getEmployedDate();
            LocalDate currentDate = LocalDate.now();
            Period period = Period.between(employedDate, currentDate);
            System.out.println("period값: " + period); // 예시 P5D

            int years = period.getYears();      // 년
            int months = period.getMonths();    // 개월
            int days = period.getDays();

            String yearsMonthString = "";
            if (years > 0) {
                yearsMonthString += years + "년 ";
            } if (months > 0) {
                yearsMonthString += months + "개월";
            } else if (years == 0 || months == 0) {
                yearsMonthString += days + "일";
            }
            System.out.println("근속년수: " + yearsMonthString);

            System.out.println("member status: " + member.getMemberStatus());

        }
        // 근속년수 작성할 것
        return memberLists;
    }
    /** 구성원 비밀번호 초기화 */
    @PutMapping("/resetMemberPassword")
    public String resetMemberPassword() {
        MemberDTO memberInfo = memberService.findSpecificMember(getTokenInfo().getMemberId());
        memberInfo.setPassword("0000");

        memberInfo.setPassword("0000");
        memberInfo.setPassword(passwordEncoder.encode(memberInfo.getPassword()));
        memberService.resetPassword(memberInfo);

        /* 아직 ing */
        return null;
    }

    /** 엑셀 파일로 구성원 정보 다운로드 */
    @GetMapping("/downloadMemberInfo")
    public ResponseEntity<Resource> downloadMemberInfo() {

        Workbook workbook = createExcelFile();

        String fileName = "전체-구성원-정보.xlsx";
        File file = new File(fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

        Resource resource = new FileSystemResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);

        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_OCTET_STREAM).body(resource);
    }

    private Workbook createExcelFile() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("구성원 정보");

        /* excel 파일 header 설정 */
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("구성원 ID");
        headerRow.createCell(1).setCellValue("이름");
        headerRow.createCell(2).setCellValue("이메일");
        headerRow.createCell(3).setCellValue("주소");
        headerRow.createCell(4).setCellValue("전화번호");
        headerRow.createCell(5).setCellValue("입사 일자");
        headerRow.createCell(6).setCellValue("부서명");
        headerRow.createCell(7).setCellValue("직급명");
        headerRow.createCell(8).setCellValue("상태");

        List<Member> members = memberService.downloadAllMembers();

        int rowNum = 1;
        for (Member member : members) {

            /* 입사일 변환 */
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            String formattedEmployedDate = member.getEmployedDate().format(formatter);


            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(member.getMemberId());
            row.createCell(1).setCellValue(member.getName());
            row.createCell(2).setCellValue(member.getEmail());
            row.createCell(3).setCellValue(member.getAddress());
            row.createCell(4).setCellValue(member.getPhoneNo());
            row.createCell(5).setCellValue(formattedEmployedDate);
            row.createCell(6).setCellValue(member.getDepartment().getDepartName());
            row.createCell(7).setCellValue(member.getPosition().getPositionName());
            row.createCell(8).setCellValue(member.getMemberStatus());
        }
        return workbook;
    }

//    @PostMapping("/login")
//    public void login(@RequestBody MemberDTO memberDTO) {
//        System.out.println("controller 도착");
//        memberService.loggedInMember(memberDTO);
//    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO) {
        System.out.println("inputted username: " + loginDTO.getMemberId());
        System.out.println("inputted password: " + loginDTO.getPassword());

        MemberDTO getMemberInfo = memberService.checkLoggedMemberInfo(loginDTO.getMemberId());

        if (getMemberInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username");
        } else if (getMemberInfo != null && !passwordEncoder.matches(loginDTO.getPassword(), getMemberInfo.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        } else {
            return ResponseEntity.ok("Login successful");
        }
    }
}