package com.igexin.log.restapi.controller;

import com.igexin.log.restapi.GlobalReference;
import com.igexin.log.restapi.RestApiProperties;
import com.igexin.log.restapi.entity.Config;
import com.igexin.log.restapi.entity.LogFileProperties;
import com.igexin.log.restapi.entity.UserInfo;
import com.igexin.log.restapi.mongod.MongoConfigRepository;
import com.igexin.log.restapi.mongod.MongoDecryptErrorRepository;
import com.igexin.log.restapi.mongod.MongoLogLineRepository;
import com.igexin.log.restapi.mongod.MongoUserInfoRepository;
import com.igexin.log.restapi.parse.LogFileParseTask;
import com.igexin.log.restapi.util.Checksum;
import com.igexin.log.restapi.util.ControllerUtil;
import com.igexin.log.restapi.util.StringUtil;
import com.igexin.log.restapi.util.VersionUtil;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
public class LogRestController {

    @Autowired
    private RestApiProperties restApiProperties;

    @Autowired
    private MongoLogLineRepository mongoDbLogLineRepository;

    @Autowired
    private MongoUserInfoRepository mongoUserInfoRepository;

    @Autowired
    private MongoDecryptErrorRepository mongoDecryptErrorRepository;

    @Autowired
    private MongoConfigRepository mongoConfigRepository;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * Upload log file.
     *
     * @param sdkVersion Sdk Version
     * @param platform   Platform
     * @param uid        User unique id
     * @param appId      Application id
     * @param loggerName Logger name
     * @param layouts    Message layout template
     * @param level      Log level
     * @param alias      User alias
     * @param fileSum    File MD5 sum
     * @param logFile    Log file
     * @return Response result
     */
    @RequestMapping(value = "/log/file/upload",
            method = RequestMethod.POST,
            produces = ControllerUtil.CONTENT_TYPE,
            headers = ControllerUtil.HEADER)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String uploadLogFile(@RequestParam("sdkVersion") String sdkVersion,
                                @RequestParam("platform") String platform,
                                @RequestParam("uid") String uid,
                                @RequestParam("appId") String appId,
                                @RequestParam("loggerName") String loggerName,
                                @RequestParam("layouts") String layouts,
                                @RequestParam("level") String level,
                                @RequestParam("alias") String alias,
                                @RequestParam("fileSum") String fileSum,
                                @RequestParam("logFile") MultipartFile logFile) {
        if (!ControllerUtil.checkPlatform(platform)) {
            throw new BadRequestException();
        }

        switch (VersionUtil.version(sdkVersion)) {
            case V1:
                return v1UploadLogFile(platform, uid, appId, loggerName, layouts, level, alias, fileSum, logFile);
            default:
                throw new BadRequestException("Unknown version!");
        }
    }

    /**
     * Upload system info.
     *
     * @param webRequest WebRequest
     * @return Config response
     */
    @RequestMapping(value = "/log/info/upload",
            method = RequestMethod.POST,
            produces = ControllerUtil.CONTENT_TYPE,
            headers = ControllerUtil.HEADER)
    @ResponseStatus(HttpStatus.OK)
    public String uploadSystemInfo(final WebRequest webRequest) {
        String platform = webRequest.getParameter("platform");
        if (!ControllerUtil.checkPlatform(platform)) {
            throw new BadRequestException();
        }

        String sdkVersion = webRequest.getParameter("sdkVersion");
        if (StringUtil.isEmpty(sdkVersion)) {
            throw new BadRequestException("No version specify!");
        }

        switch (VersionUtil.version(sdkVersion)) {
            case V1:
                return v1UploadSystemInfo(webRequest);
            default:
                throw new BadRequestException("Unknown version!");
        }
    }

    /**
     * Upload crash report file.
     *
     * @param sdkVersion Sdk Version
     * @param platform   Platform
     * @param uid        User unique id
     * @param appId      Application id
     * @param fileSum    File MD5 sum
     * @param reportFile Crash report file
     * @return Response result
     */
    @RequestMapping(value = "/log/crash/upload",
            method = RequestMethod.POST,
            produces = ControllerUtil.CONTENT_TYPE,
            headers = ControllerUtil.HEADER)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String uploadCrashReport(@RequestParam("sdkVersion") String sdkVersion,
                                    @RequestParam("platform") String platform,
                                    @RequestParam("uid") String uid,
                                    @RequestParam("appId") String appId,
                                    @RequestParam("fileSum") String fileSum,
                                    @RequestParam("reportFile") MultipartFile reportFile) {
        if (!ControllerUtil.checkPlatform(platform)) {
            throw new BadRequestException();
        }

        switch (VersionUtil.version(sdkVersion)) {
            case V1:
                return v1UploadCrashReport(platform, uid, appId, fileSum, reportFile);
            default:
                throw new BadRequestException("Unknown version!");
        }
    }

    /**
     * Upload attachment.
     *
     * @param sdkVersion     Sdk Version
     * @param platform       Platform
     * @param uid            User unique id
     * @param appId          Application id
     * @param fileSum        File MD5 sum
     * @param attachmentId   Attachment id
     * @param attachmentFile Attachment file
     * @return Response result
     */
    @RequestMapping(value = "/log/attachment/upload",
            method = RequestMethod.POST,
            produces = ControllerUtil.CONTENT_TYPE,
            headers = ControllerUtil.HEADER)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String uploadAttachment(@RequestParam("sdkVersion") String sdkVersion,
                                   @RequestParam("platform") String platform,
                                   @RequestParam("uid") String uid,
                                   @RequestParam("appId") String appId,
                                   @RequestParam("fileSum") String fileSum,
                                   @RequestParam("attachmentId") String attachmentId,
                                   @RequestParam("attachmentFile") MultipartFile attachmentFile) {
        if (!ControllerUtil.checkPlatform(platform)) {
            throw new BadRequestException();
        }

        switch (VersionUtil.version(sdkVersion)) {
            case V1:
                return v1UploadAttachment(platform, uid, appId, fileSum, attachmentId, attachmentFile);
            default:
                throw new BadRequestException("Unknown version!");
        }
    }

    // --------------------------------------------- Old api ------------------------------------------------------ //

    /**
     * Upload log file
     *
     * @param platform   Platform
     * @param uid        User unique id
     * @param appId      Application id
     * @param loggerName Logger name
     * @param layouts    Message layout template
     * @param level      Log level
     * @param alias      User alias
     * @param fileSum    File MD5 sum
     * @param logFile    Log file
     * @return Response result
     */
    @RequestMapping(value = "/log/uploadLogFile",
            method = RequestMethod.POST,
            produces = ControllerUtil.CONTENT_TYPE,
            headers = ControllerUtil.HEADER)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String oldUploadLogFile(@RequestParam("platform") String platform,
                                   @RequestParam("uid") String uid,
                                   @RequestParam("appId") String appId,
                                   @RequestParam("loggerName") String loggerName,
                                   @RequestParam("layouts") String layouts,
                                   @RequestParam("level") String level,
                                   @RequestParam("alias") String alias,
                                   @RequestParam("fileSum") String fileSum,
                                   @RequestParam("logFile") MultipartFile logFile) {
        if (!ControllerUtil.checkPlatform(platform)) {
            throw new BadRequestException();
        }

        return v1UploadLogFile(platform, uid, appId, loggerName, layouts, level, alias, fileSum, logFile);
    }

    /**
     * Upload system info file
     *
     * @param platform Platform
     * @param uid      User unique id
     * @param appId    Application id
     * @param fileSum  File MD5 sum
     * @param infoFile System info file
     * @return Response result
     */
    @RequestMapping(value = "/log/uploadSystemInfo",
            method = RequestMethod.POST,
            produces = ControllerUtil.CONTENT_TYPE,
            headers = ControllerUtil.HEADER)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String oldUploadSystemInfo(@RequestParam("platform") String platform,
                                      @RequestParam("uid") String uid,
                                      @RequestParam("appId") String appId,
                                      @RequestParam("fileSum") String fileSum,
                                      @RequestParam("infoFile") MultipartFile infoFile) {
        return "{}";
    }

    /**
     * Upload crash report file
     *
     * @param platform   Platform
     * @param uid        User unique id
     * @param appId      Application id
     * @param fileSum    File MD5 sum
     * @param reportFile Crash report file
     * @return Response result
     */
    @RequestMapping(value = "/log/uploadCrashReport",
            method = RequestMethod.POST,
            produces = ControllerUtil.CONTENT_TYPE,
            headers = ControllerUtil.HEADER)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String oldUploadCrashReport(@RequestParam("platform") String platform,
                                       @RequestParam("uid") String uid,
                                       @RequestParam("appId") String appId,
                                       @RequestParam("fileSum") String fileSum,
                                       @RequestParam("reportFile") MultipartFile reportFile) {
        if (!ControllerUtil.checkPlatform(platform)) {
            throw new BadRequestException();
        }

        return v1UploadCrashReport(platform, uid, appId, fileSum, reportFile);
    }

    // ---------------------------------- Detail rest controller version function api ------------------------------ //

    private String v1UploadLogFile(String platform,
                                   String uid,
                                   String appId,
                                   String loggerName,
                                   String layouts,
                                   String level,
                                   String alias,
                                   String fileSum,
                                   MultipartFile logFile) {
        GlobalReference.saveProperties(restApiProperties);
        GlobalReference.saveLogLineRepository(mongoDbLogLineRepository);
        GlobalReference.saveDecryptErrorRepository(mongoDecryptErrorRepository);
        GlobalReference.listen();

        String tempDirPath = restApiProperties.tempDir();
        File tempDir = new File(tempDirPath);
        if (!tempDir.exists()) {
            if (tempDir.mkdirs()) {
                throw new ServerException();
            }
        }

        String filename = StringUtil.randomUid();
        String originalFilename = logFile.getOriginalFilename();
        String filePath = tempDirPath + "/" + filename;
        File file = new File(filePath);
        try {
            logFile.transferTo(file);
            // Check uploaded file sum.
            String sum = Checksum.fileMD5(file.getAbsolutePath());
            if (!sum.equalsIgnoreCase(fileSum)) {
                throw new ServerException();
            }
        } catch (IOException e) {
            throw new ServerException();
        }

        LogFileProperties properties = new LogFileProperties();
        properties.setPlatform(platform);
        properties.setUid(uid);
        properties.setAppId(appId);
        properties.setLevel(Integer.parseInt(level));
        properties.setLoggerName(loggerName);
        properties.setAlias(alias);
        properties.setLayouts(layouts);
        properties.setFilename(filename);
        properties.setOriginalFilename(originalFilename);
        properties.setWorkPath(tempDirPath);

        LogFileParseTask task = LogFileParseTask.create(properties);
        threadPoolTaskExecutor.submit(task);

        return responseJson(0, "");
    }

    private String v1UploadSystemInfo(final WebRequest webRequest) {
        // Save user info to db.
        UserInfo info = UserInfo.create(webRequest);
        UserInfo temp = mongoUserInfoRepository.findByUidAndAppId(info.getPlatform(), info.getUid(), info.getAppId());
        if (temp != null) {
            if (temp.hashCode() != info.hashCode()) {
                info.setId(temp.getId());
                mongoUserInfoRepository.save(info);
            }
        } else {
            mongoUserInfoRepository.save(info);
        }

        Config config = mongoConfigRepository.read();

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("result", "ok");
        jsonObject.put("upload", info.getLevel() <= config.getLevel());
        jsonObject.put("description", "");

        return jsonObject.toString();
    }

    private String v1UploadCrashReport(String platform,
                                       String uid,
                                       String appId,
                                       String fileSum,
                                       MultipartFile reportFile) {
        String reportDirPath = restApiProperties.crashReportDir(platform) + "/" + appId + "/" + uid;
        File reportDir = new File(reportDirPath);
        if (!reportDir.exists()) {
            if (!reportDir.mkdirs()) {
                // Create crash report dir failed
                throw new ServerException();
            }
        }

        File file = new File(reportDirPath + "/" + reportFile.getOriginalFilename());
        try {
            reportFile.transferTo(file);
            // Check uploaded file sum
            String fileSumString = Checksum.fileMD5(file.getAbsolutePath());
            if (!fileSumString.equalsIgnoreCase(fileSum)) {
                throw new ServerException();
            }
        } catch (IOException e) {
            throw new ServerException();
        }

        //TODO

        return responseJson(0, "");
    }

    private String v1UploadAttachment(String platform,
                                      String uid,
                                      String appId,
                                      String fileSum,
                                      String attachmentId,
                                      MultipartFile attachmentFile) {
        String filename = StringUtil.attachmentName(platform, uid, appId, attachmentId);
        if (filename.length() > 0) {
            File dir = new File(restApiProperties.attachmentDir());
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new ServerException();
                }
            }
            String filePath = restApiProperties.attachmentDir() + "/" + filename + ".jpg";
            File file = new File(filePath);

            try {
                attachmentFile.transferTo(file);
                // Check uploaded file sum
                String fileSumString = Checksum.fileMD5(file.getAbsolutePath());
                if (!fileSumString.equalsIgnoreCase(fileSum)) {
                    throw new ServerException();
                }
            } catch (IOException e) {
                throw new ServerException();
            }
        }

        return responseJson(0, "");
    }

    /**
     * Response json result.
     *
     * @param result      Result code
     * @param description Description message
     * @return JSON string
     */
    private String responseJson(int result, String description) {
        JSONObject object = new JSONObject();
        object.put("result", result);
        object.put("description", description);
        return object.toString();
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public class BadRequestException extends RuntimeException {

        public BadRequestException() {
            super();
        }

        public BadRequestException(String message) {
            super(message);
        }
    }

    @ResponseStatus(value = HttpStatus.EXPECTATION_FAILED)
    public class ServerException extends RuntimeException {

    }

}
