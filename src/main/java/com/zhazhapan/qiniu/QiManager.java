/**
 * 
 */
package com.zhazhapan.qiniu;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;

import org.apache.log4j.Logger;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.model.BatchStatus;
import com.zhazhapan.qiniu.config.QiConfig;
import com.zhazhapan.qiniu.controller.MainWindowController;
import com.zhazhapan.qiniu.model.FileInfo;
import com.zhazhapan.qiniu.modules.constant.Values;
import com.zhazhapan.qiniu.util.Checker;
import com.zhazhapan.qiniu.util.Formatter;
import com.zhazhapan.qiniu.view.Dialogs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * @author pantao
 *
 */
public class QiManager {

	private Logger logger = Logger.getLogger(QiManager.class);

	public enum FileAction {
		// 复制或移动文件
		COPY, MOVE
	}

	/**
	 * 私有下载
	 */
	public void privateDownload(String fileName, String domain) {
		try {
			String encodedFileName = URLEncoder.encode(fileName, "utf-8");
			String publicUrl = String.format("%s/%s", domain, encodedFileName);
			// 自定义链接过期时间（小时）
			long expireInSeconds = 24;
			new Downloader().downloadFromNet(QiniuApplication.auth.privateDownloadUrl(publicUrl, expireInSeconds));
		} catch (UnsupportedEncodingException e) {
			urlError(e);
		}
	}

	/**
	 * 公有下载
	 */
	public void publicDownload(String fileName, String domain) {
		String url = getPublicURL(fileName, domain);
		if (Checker.isNotEmpty(url)) {
			new Downloader().downloadFromNet(url);
		}
	}

	public String getPublicURL(String fileName, String domain) {
		try {
			String encodedFileName = URLEncoder.encode(fileName, "utf-8");
			return String.format("%s/%s", domain, encodedFileName);
		} catch (UnsupportedEncodingException e) {
			urlError(e);
			return null;
		}
	}

	/**
	 * 更新镜像源
	 */
	public void updateFile(String bucket, String key) {
		String log = "update file '" + key + "' on bucket '" + bucket;
		try {
			QiniuApplication.bucketManager.prefetch(bucket, key);
			logger.info(log + "' success");
		} catch (QiniuException e) {
			logger.error(log + "' error, message: " + e.getMessage());
			Dialogs.showException(Values.UPDATE_ERROR, e);
		}
	}

	/**
	 * 设置文件生存时间
	 */
	public void setFileLife(String bucket, String key, int days) {
		String log = "set file of '" + key + "' life to " + days + " day(s) ";
		try {
			QiniuApplication.bucketManager.deleteAfterDays(bucket, key, days);
			logger.info(log + "success");
		} catch (QiniuException e) {
			logger.error(log + "error, message: " + e.getMessage());
			Dialogs.showException(Values.MOVE_OR_RENAME_ERROR, e);
		}
	}

	/**
	 * 重命令文件
	 */
	public Boolean renameFile(String bucket, String oldName, String newName) {
		return moveFile(bucket, oldName, bucket, newName);
	}

	/**
	 * 移动文件
	 */
	public boolean moveFile(String fromBucket, String fromKey, String toBucket, String toKey) {
		return moveOrCopyFile(fromBucket, fromKey, toBucket, toKey, FileAction.MOVE);
	}

	/**
	 * 移动或复制文件
	 */
	public boolean moveOrCopyFile(String fromBucket, String fromKey, String toBucket, String toKey, FileAction action) {
		if (new QiConfig().checkNet()) {
			String log = "move file '" + fromKey + "' from bucket '" + fromBucket + "' to bucket '" + toBucket
					+ "', and rename file '" + toKey + "'";
			try {
				if (action == FileAction.COPY) {
					log = log.replaceAll("^move", "copy");
					QiniuApplication.bucketManager.copy(fromBucket, fromKey, toBucket, toKey, true);
				} else {
					QiniuApplication.bucketManager.move(fromBucket, fromKey, toBucket, toKey, true);
				}
				logger.info(log + " success");
				return true;
			} catch (QiniuException e) {
				logger.error(log + " failed, message: " + e.getMessage());
				Dialogs.showException(Values.MOVE_OR_RENAME_ERROR, e);
				return false;
			}
		}
		return false;
	}

	/**
	 * 修改文件类型
	 */
	public boolean changeType(String fileName, String newType, String bucket) {
		if (new QiConfig().checkNet()) {
			String log = "change file '" + fileName + "' type '" + newType + "' on bucket '" + bucket;
			try {
				QiniuApplication.bucketManager.changeMime(bucket, fileName, newType);
				logger.info(log + "' success");
				return true;
			} catch (QiniuException e) {
				logger.error(log + "' failed, message: " + e.getMessage());
				Dialogs.showException(Values.CHANGE_FILE_TYPE_ERROR, e);
				return false;
			}
		}
		return false;
	}

	/**
	 * 批量删除文件，单次批量请求的文件数量不得超过1000
	 */
	public void deleteFiles(ObservableList<FileInfo> fileInfos, String bucket) {
		if (Checker.isNotEmpty(fileInfos) && new QiConfig().checkNet()) {
			// 生成待删除的文件列表
			String[] files = new String[fileInfos.size()];
			ArrayList<FileInfo> seletecFileInfos = new ArrayList<FileInfo>();
			int i = 0;
			for (FileInfo fileInfo : fileInfos) {
				files[i] = fileInfo.getName();
				seletecFileInfos.add(fileInfo);
				i++;
			}
			try {
				BucketManager.BatchOperations batchOperations = new BucketManager.BatchOperations();
				batchOperations.addDeleteOp(bucket, files);
				Response response = QiniuApplication.bucketManager.batch(batchOperations);
				BatchStatus[] batchStatusList = response.jsonToObject(BatchStatus[].class);
				MainWindowController main = MainWindowController.getInstance();
				boolean sear = Checker.isNotEmpty(main.searchTextField.getText());
				ObservableList<FileInfo> currentRes = main.resTable.getItems();
				for (i = 0; i < files.length; i++) {
					BatchStatus status = batchStatusList[i];
					String deleteLog = Formatter.datetimeToString(new Date());
					String file = files[i];
					if (status.code == 200) {
						logger.info("delete file '" + file + "' success");
						deleteLog += "\tsuccess\t";
						QiniuApplication.data.remove(seletecFileInfos.get(i));
						if (sear) {
							currentRes.remove(seletecFileInfos.get(i));
						}
					} else {
						logger.error("delete file '" + file + "' failed, message: " + status.data.error);
						deleteLog += "\tfailed\t";
						Dialogs.showError("删除文件：" + file + " 失败");
					}
					QiniuApplication.deleteLog.append(deleteLog + bucket + "\t" + file + "\r\n");
				}
			} catch (QiniuException e) {
				Dialogs.showException(Values.DELETE_ERROR, e);
			}
		}
	}

	/**
	 * 获取空间文件列表，并映射到FileInfo
	 */
	public void listFileOfBucket() {
		MainWindowController main = MainWindowController.getInstance();
		// 列举空间文件列表
		String bucket = main.bucketChoiceCombo.getValue();
		BucketManager.FileListIterator iterator = QiniuApplication.bucketManager.createFileListIterator(bucket, "",
				Values.BUCKET_LIST_LIMIT_SIZE, "");
		ArrayList<FileInfo> files = new ArrayList<FileInfo>();
		logger.info("get file list of bucket: " + bucket);
		// 处理获取的file list结果
		while (iterator.hasNext()) {
			com.qiniu.storage.model.FileInfo[] items = iterator.next();
			for (com.qiniu.storage.model.FileInfo item : items) {
				// 将七牛的时间单位（100纳秒）转换成毫秒，然后转换成时间
				String time = Formatter.timeStampToString(item.putTime / 10000);
				String size = Formatter.formatSize(item.fsize);
				FileInfo file = new FileInfo(item.key, item.mimeType, size, time);
				files.add(file);
				logger.info("file name: " + item.key + ", file type: " + item.mimeType + ", file size: " + size
						+ ", file time: " + time);
			}
		}
		QiniuApplication.data = FXCollections.observableArrayList(files);
	}

	private void urlError(Exception e) {
		logger.error("generate url error: " + e.getMessage());
		Dialogs.showException(Values.GENERATE_URL_ERROR, e);
	}
}
