package com.zoick.farmmarket.infrastructure.scheduling;
import com.zoick.farmmarket.domain.order.Reservation;
import com.zoick.farmmarket.domain.order.ReservationRepository;
import com.zoick.farmmarket.domain.order.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
/*
Runs every 2min, checks for reservations that have expired and updates their status
Each reservation has a expiry time, if the current time is past the expiry time, the reservation is cancelled
each iteration is its own save-> if one fails, others are not affected
 */
public class ReservationExpiryJob {
    private final ReservationRepository reservationRepository;
    private final ReservationExpiryProcessor processor;

    @Scheduled(fixedDelay = 120000)
    public void expireReservations(){
        LocalDateTime now= LocalDateTime.now();
        List<Reservation> expired= reservationRepository.findAllByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE,
                now);
        if(expired.isEmpty()){
            return;
        }
        log.info("ReservationExpiryJob: processing {} reservations",
                expired.size());
        for(Reservation reservation : expired){
            try{
                processor.expireOne(reservation.getId());
            } catch (Exception e) {
                log.error("Failed to expire reservation {}: {}", reservation.getId(), e.getMessage());
            }
        }
        log.info("ReservationExpiryJob: completed processing {} reservations", expired.size());
    }
}
