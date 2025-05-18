package com.transit.graphbased_v2.performacelogging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;


@Aspect
@Component
@Slf4j
public class MyAspect {
	
	
	@Around("@annotation(com.transit.graphbased_v2.performacelogging.LogExecutionTime)")
	public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
		final StopWatch stopWatch = new StopWatch();
		
		stopWatch.start();
		
		Object proceed = joinPoint.proceed();
		
		stopWatch.stop();
		
		//	log.error("\"{}\" executed in {}", joinPoint.getSignature(), DurationFormatUtils.formatDurationHMS(stopWatch.getTotalTimeMillis()));
		
		return proceed;
	}
	
}