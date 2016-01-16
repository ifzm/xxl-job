package com.xxl.job.service.job;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.xxl.job.client.handler.HandlerRepository;
import com.xxl.job.client.util.HttpUtil;
import com.xxl.job.client.util.JacksonUtil;
import com.xxl.job.core.model.XxlJobInfo;
import com.xxl.job.core.model.XxlJobLog;
import com.xxl.job.core.util.DynamicSchedulerUtil;
import com.xxl.job.core.util.PropertiesUtil;

/**
 * http job bean
 * @author xuxueli 2015-12-17 18:20:34
 */
public class HttpJobBean extends QuartzJobBean {
	private static Logger logger = LoggerFactory.getLogger(HttpJobBean.class);

	@SuppressWarnings("unchecked")
	@Override
	protected void executeInternal(JobExecutionContext context)
			throws JobExecutionException {
		String triggerKey = context.getTrigger().getJobKey().getName();
		
		// jobDataMap 2 params
		Map<String, String> params = new HashMap<String, String>();
		XxlJobInfo jobInfo = DynamicSchedulerUtil.xxlJobInfoDao.load(triggerKey);
		if (jobInfo!=null && jobInfo.getJobData()!=null) {
			params = JacksonUtil.readValue(jobInfo.getJobData(), Map.class);
		}
		
		// corn
		String cornExp = null;
		if (context.getTrigger() instanceof CronTriggerImpl) {
			CronTriggerImpl trigger = (CronTriggerImpl) context.getTrigger();
			cornExp = trigger.getCronExpression();
		}
		
		// save log
		XxlJobLog jobLog = new XxlJobLog();
		jobLog.setJobName(triggerKey);
		jobLog.setJobCron(cornExp);
		jobLog.setJobClass(HttpJobBean.class.getName());
		jobLog.setJobData(jobInfo.getJobData());
		DynamicSchedulerUtil.xxlJobLogDao.save(jobLog);
		logger.info(">>>>>>>>>>> xxl-job trigger start, jobLog:{}", jobLog);
		
		// trigger request
		params.put(HandlerRepository.triggerLogId, String.valueOf(jobLog.getId()));
		params.put(HandlerRepository.triggerLogUrl, PropertiesUtil.getString(HandlerRepository.triggerLogUrl));
		String[] postResp = HttpUtil.post(params.get(HandlerRepository.job_url), params);
		logger.info(">>>>>>>>>>> xxl-job trigger http response, jobLog.id:{}, jobLog:{}", jobLog.getId(), jobLog);
		
		// parse trigger response
		String responseMsg = postResp[0];
		String exceptionMsg = postResp[1];
		
		jobLog.setTriggerTime(new Date());
		jobLog.setTriggerStatus(HttpUtil.FAIL);
		jobLog.setTriggerMsg("[responseMsg]:"+responseMsg+"<br>[exceptionMsg]:"+exceptionMsg);
		if (StringUtils.isNotBlank(responseMsg) && responseMsg.indexOf("{")>-1 ) {
			Map<String, String> responseMap = JacksonUtil.readValue(responseMsg, Map.class);
			if (responseMap!=null && StringUtils.isNotBlank(responseMap.get(HttpUtil.status))) {
				jobLog.setTriggerStatus(responseMap.get(HttpUtil.status));
				jobLog.setTriggerMsg(responseMap.get(HttpUtil.msg));
			}
		}
		
		// update trigger info
		DynamicSchedulerUtil.xxlJobLogDao.updateTriggerInfo(jobLog);
		logger.info(">>>>>>>>>>> xxl-job trigger end, jobLog.id:{}, jobLog:{}", jobLog.getId(), jobLog);
		
    }
	
}