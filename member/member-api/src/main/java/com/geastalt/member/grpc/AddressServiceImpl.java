package com.geastalt.member.grpc;

import com.geastalt.member.dto.usps.AddressRequest;
import com.geastalt.member.dto.usps.AddressResponse;
import com.geastalt.member.dto.usps.StandardizedAddressResult;
import com.geastalt.member.service.AddressStandardizationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AddressServiceImpl extends AddressServiceGrpc.AddressServiceImplBase {

    private final AddressStandardizationService addressStandardizationService;

    @Override
    public void standardizeAddress(StandardizeAddressRequest request,
                                   StreamObserver<StandardizeAddressResponse> responseObserver) {
        log.info("gRPC StandardizeAddress called for: {}, {}, {}",
                request.getStreetAddress(),
                request.getCity(),
                request.getState());

        try {
            AddressRequest uspsRequest = AddressRequest.builder()
                    .streetAddress(request.getStreetAddress())
                    .secondaryAddress(request.getSecondaryAddress().isEmpty() ? null : request.getSecondaryAddress())
                    .city(request.getCity())
                    .state(request.getState())
                    .zipCode(request.getZipCode().isEmpty() ? null : request.getZipCode())
                    .zipPlus4(request.getZipPlus4().isEmpty() ? null : request.getZipPlus4())
                    .build();

            StandardizedAddressResult result = addressStandardizationService.standardizeAndSaveAddress(uspsRequest);
            AddressResponse uspsResponse = result.getUspsResponse();

            StandardizeAddressResponse.Builder responseBuilder = StandardizeAddressResponse.newBuilder();

            if (uspsResponse.getAddress() != null) {
                Address.Builder addressBuilder = Address.newBuilder();
                AddressResponse.Address addr = uspsResponse.getAddress();

                addressBuilder.setId(result.getId());
                if (addr.getStreetAddress() != null) {
                    addressBuilder.setStreetAddress(addr.getStreetAddress());
                }
                if (addr.getSecondaryAddress() != null) {
                    addressBuilder.setSecondaryAddress(addr.getSecondaryAddress());
                }
                if (addr.getCity() != null) {
                    addressBuilder.setCity(addr.getCity());
                }
                if (addr.getState() != null) {
                    addressBuilder.setState(addr.getState());
                }
                if (addr.getZipCode() != null) {
                    addressBuilder.setZipCode(addr.getZipCode());
                }
                if (addr.getZipPlus4() != null) {
                    addressBuilder.setZipPlus4(addr.getZipPlus4());
                }

                responseBuilder.setAddress(addressBuilder.build());
            }

            if (uspsResponse.getDeliveryPoint() != null) {
                responseBuilder.setDeliveryPoint(uspsResponse.getDeliveryPoint());
            }
            if (uspsResponse.getCarrierRoute() != null) {
                responseBuilder.setCarrierRoute(uspsResponse.getCarrierRoute());
            }
            if (uspsResponse.getDPVConfirmation() != null) {
                responseBuilder.setDpvConfirmation(uspsResponse.getDPVConfirmation());
            }
            if (uspsResponse.getDPVCMRA() != null) {
                responseBuilder.setDpvCmra(uspsResponse.getDPVCMRA());
            }
            if (uspsResponse.getBusiness() != null) {
                responseBuilder.setBusiness(uspsResponse.getBusiness());
            }
            if (uspsResponse.getCentralDeliveryPoint() != null) {
                responseBuilder.setCentralDeliveryPoint(uspsResponse.getCentralDeliveryPoint());
            }
            if (uspsResponse.getVacant() != null) {
                responseBuilder.setVacant(uspsResponse.getVacant());
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error standardizing address", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to standardize address: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
