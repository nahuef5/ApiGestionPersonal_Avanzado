package com.management.staff.services.staffService;
import com.management.staff.dto.staffDto.*;
import com.management.staff.entities.Staff;
import com.management.staff.global.exceptions.*;
import com.management.staff.global.utils.MessageHandler;
import com.management.staff.models.*;
import com.management.staff.repository.StaffRepository;
import com.management.staff.security.services.userService.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional
public class StaffServiceImpl implements StaffServiceInterface{
    @Autowired
    private StaffRepository repository;
    
    private StaffDto staffDto=new StaffDto();
    
    //SETEADOR STAFF VISTA 
    private void setDto(int dni) throws ResourceNotFoundException{
        Staff staff = repository.findByDni(dni).orElseThrow(()-> new ResourceNotFoundException(MessageHandler.NOT_FOUD));                
        StaffDto dto=new StaffDto(
                    staff.getName(),
                    staff.getSurname(),
                    staff.getAddress(),
                    staff.getDni(),
                    staff.getBorn(),
                    staff.getArea().getArea().name(),
                    staff.getPosition().getPosition().name(),
                    staff.getContractStart(),
                    staff.getEmail(),
                    staff.getBasicSalary(),
                    staff.getGrossSalary(),
                    staff.getNetSalary()
                    );
            this.staffDto=dto;
    }
    //obtener dni desde el objeto autenticado
    private int getDniFromAuthentication(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userDetails.getDni();
    }
    @Override
    public StaffDto getOneStaff(int dni)throws ResourceNotFoundException, Exception {
        //obtengo el usuario autenticado
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        //podra acceder al recurso solo si comparte dni o si tiene rol admintrainee
        if(
            dni == getDniFromAuthentication(authentication)
                ||
            authentication
                .getAuthorities().stream().anyMatch(
                    auth -> auth.getAuthority().equals("ROLE_ADMINTRAINEE")
                            ||
                            auth.getAuthority().equals("ROLE_EJECUTIVO"))
                ){
            setDto(dni);
            return staffDto;
        }
        throw new Exception("No tiene permitido ver los datos de ese personal");
    }
    @Override
    public Page<Staff> getAllStaffs(QueryPageable queryPageable) throws ListEmptyException {
        Sort sort= Sort.by(Sort.Direction.fromString(
                queryPageable.getOrder()),
                queryPageable.getOrderBy());
        Pageable pageable = PageRequest.of(
                queryPageable.getPage(),
                queryPageable.getElementByPage(),
                sort);
        return repository.findAll(pageable);
    }
    //modificar sueldo
    @Override
    public void setGrossSalary(GrossSalaryStaffDto dto, int dni){
        Staff staff=repository.findByDni(dni).get();
        
        double auxGross=staff.getBasicSalary();
        double presentismo=staff.getBasicSalary()*Salary.PRESENTEEISM;
        double antiguedad=staff.getBasicSalary()*(Salary.ANTIQUITY*dto.getQuantityAntiquity());
        double extras=Salary.valueExtraHours(staff.getBasicSalary())*dto.getQuantityExtraHours();
        
        if(dto.isPresenteeism())
            auxGross += presentismo;
  
        auxGross += antiguedad + extras;
        staff.setGrossSalary(auxGross);
    }
    @Override
    public void setNetSalary(GrossSalaryStaffDto dto, int dni){
        Staff staff=repository.findByDni(dni).get();
        double auxNet=staff.getGrossSalary();
        
        if(dto.isAfiliado())
            auxNet -= staff.getGrossSalary()*Salary.SINDICATO;
        
        auxNet -= auxNet*Salary.INSSJP+
                  auxNet*Salary.OBRA_SOCIAL+
                  auxNet*Salary.PENSION;
            staff.setNetSalary(auxNet);
    }        
    @Override
    public MessageHandler updateStaffSalary(GrossSalaryStaffDto dto, int dni)throws ResourceNotFoundException{
        Staff staff=repository.findByDni(dni)
                .orElseThrow(
                        ()->new ResourceNotFoundException(MessageHandler.NOT_FOUD)
                );
        setGrossSalary(dto, dni);
        System.out.println(staff.getGrossSalary());
        setNetSalary(dto, dni);
        return new MessageHandler("Sueldo actualizado correctamente",HttpStatus.OK);
    }
}