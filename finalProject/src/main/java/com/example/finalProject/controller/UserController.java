package com.example.finalProject.controller;

import com.example.finalProject.entity.AppUser;
import com.example.finalProject.entity.Parent;
import com.example.finalProject.models.AuthRequest;
import com.example.finalProject.entity.TemporaryUser;
import com.example.finalProject.models.UpdateLocationRequest;
import com.example.finalProject.models.VerificationRequest;
import com.example.finalProject.repository.ParentRepository;
import com.example.finalProject.repository.TemporaryUserRepository;
import com.example.finalProject.repository.UserRepository;
import com.example.finalProject.service.SendEmailService;
import com.example.finalProject.util.JwtUtil;

import org.hibernate.annotations.Parent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.security.Principal;
import java.time.LocalDate;


@RestController
public class UserController {
    @Autowired
    SendEmailService sendEmailService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TemporaryUserRepository temporaryUserRepository;

    @Autowired
    private ParentRepository parentRepository;

    @PostMapping("/authenticate")
    public String generateToken(@RequestBody AuthRequest authrequest) throws Exception {
        try {
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken=new UsernamePasswordAuthenticationToken(authrequest.getUserName(), authrequest.getPassword());
            System.out.println(usernamePasswordAuthenticationToken);
            authenticationManager.authenticate(usernamePasswordAuthenticationToken);
        } catch (Exception e) {
            throw new Exception("Invalid username and password");
        }

        return jwtUtil.generateToken(authrequest.getUserName());
    }

    @Autowired
    private JavaMailSender emailSender;

    public void sendSimpleMessage(String to, String subject, String name, String pass) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("childappadmain@gmail.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText("Your Child " + name + " is trying to create account in our web site, is you are accepting that use this link => http://localhost:8081/parentverification. to verification the account, enter you email with this password = >" + pass + ". you can use this pass word to accept you children events sure or add posts or chat with author children");
            System.out.println("sending message");
            emailSender.send(message);
            System.out.println("done");
        } catch (Exception ex) {
            System.out.println(ex);
        }

    }

    @PostMapping("/signup")
    public RedirectView signup(@RequestBody TemporaryUser temporaryUser) {
        LocalDate now=LocalDate.now();
        System.out.println("age now ");
        System.out.println(now.getYear()-temporaryUser.getDateOfBirth().getYear());
        if(now.getYear()-temporaryUser.getDateOfBirth().getYear()<18){
            try {
                if (userRepository.findByUserName(temporaryUser.getUsername()) == null) {
                    String serialNumber = (int) (Math.random() * 10) + "" + (int) (Math.random() * 10) + (int) (Math.random() * 10) + (int) (Math.random() * 10) + (int) (Math.random() * 10) + (int) (Math.random() * 10);
                    Parent parent = parentRepository.findByParentEmail(temporaryUser.getParentEmail());
                    if(parent != null){
                        serialNumber = parent.getPassword();
                    };
                    temporaryUser.setSerialNumber(serialNumber);
                    temporaryUserRepository.save(temporaryUser);
                    System.out.println("Saved");
                    sendSimpleMessage(temporaryUser.getParentEmail(), "Verification", temporaryUser.getUsername(), temporaryUser.getSerialNumber());
                } else {
                    return new RedirectView("/error?message=already used username");
                }
            } catch (Exception ex) {
                System.out.println(ex);
                return new RedirectView("/error?message=Used%username");
            }
        }
        return new RedirectView("/");
    }




    @PostMapping("/parentverification")
    public String parentVerification(@RequestBody VerificationRequest verificationRequest) {
        try {
            TemporaryUser temporaryUser = temporaryUserRepository.findByParentEmailAndSerialNumber(verificationRequest.getParentEmail(), verificationRequest.getSerialNumber());
            if (temporaryUser != null) {
                Parent parent = parentRepository.findByParentEmail(verificationRequest.getParentEmail());
                System.out.println(parent);
                System.out.println("parent");
                if(parent == null){
                    parent = new Parent(temporaryUser.getParentEmail(), temporaryUser.getSerialNumber());
                    parent.setUserName(temporaryUser.getUserName() + " Parent");
                }else {
                    parent.setParentPassword(temporaryUser.getSerialNumber());
                }
                parent = parentRepository.save(parent);
                AppUser appUser = new AppUser(temporaryUser.getUsername(), temporaryUser.getPassword(), temporaryUser.getParentEmail(), parent,temporaryUser.getDateOfBirth(),temporaryUser.getLocation());
                userRepository.save(appUser);
                temporaryUserRepository.delete(temporaryUser);
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(appUser.getUserName(), appUser.getPassword()));
                return jwtUtil.generateToken(appUser.getUserName());
            } else {
                return "Wrong Email or Password";
            }
        } catch (Exception ex) {
            return "error";
        }
    };

    @PostMapping("/loginAsParent")
    public String paren(@RequestBody AuthRequest authrequest) {
        try {
            System.out.println(authrequest.getUserName());
            System.out.println(authrequest.getPassword());
            Parent parent = parentRepository.findByUserNameAndParentPassword(authrequest.getUserName(), authrequest.getPassword());
            System.out.println(parent);
            if (parent != null) {
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(authrequest.getUserName(), authrequest.getPassword());
                System.out.println(usernamePasswordAuthenticationToken);
                authenticationManager.authenticate(usernamePasswordAuthenticationToken);
                return jwtUtil.generateToken(authrequest.getUserName());
            } else {
                return "Wrong Email or Password";
            }
        } catch (Exception ex) {
            System.out.println(ex);
            return "error";
        }
    };

    @GetMapping("/parentProfile")
    public ResponseEntity parentProfile() {
        try {
            System.out.println((SecurityContextHolder.getContext().getAuthentication()) ==null);
            if((SecurityContextHolder.getContext().getAuthentication()) != null){
                Parent parent = parentRepository.findByUserName(SecurityContextHolder.getContext().getAuthentication().getName());
                return new ResponseEntity(parent, HttpStatus.OK);
            }
        } catch (Exception ex) {
            return new ResponseEntity( HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity( HttpStatus.NOT_ACCEPTABLE);
    };


    @GetMapping("/profile")
    public Object profile(Principal p) {
        try {
            if ((SecurityContextHolder.getContext().getAuthentication()) != null) {
                AppUser userDetails = userRepository.findByUserName(SecurityContextHolder.getContext().getAuthentication().getName());
                return userDetails;
            }
            return "login before";
        } catch (Exception ex) {
            return new RedirectView("/error?message=Used%username");
        }
    }

    //.............................................AppUser..............................................

    @PutMapping("/profile")
    public ResponseEntity updateUser(@RequestBody AppUser user) {
        try {
            if ((SecurityContextHolder.getContext().getAuthentication()) != null) {
                AppUser userDetails = userRepository.findByUserName(SecurityContextHolder.getContext().getAuthentication().getName());
                if (userDetails != null) {
                    System.out.println(user.toString());
                    userDetails.setUserName(user.getUserName());
                    userDetails.setEmail(user.getEmail());
                    userDetails.setPassword(user.getPassword());
                    userRepository.save(userDetails);
                }
            }
            return new ResponseEntity<AppUser>(HttpStatus.OK);
        } catch (Exception n) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
    }


    @GetMapping("/user")
    public ResponseEntity userByName(@RequestParam String userName) {
        try {
            if ((SecurityContextHolder.getContext().getAuthentication()) != null) {
                AppUser userDetails = userRepository.findByUserName(userName);
                System.out.println(userDetails.getEmail());
                return new ResponseEntity<AppUser>(userDetails,HttpStatus.OK);
            }
        } catch (Exception ex) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }

    @PutMapping("/userLocation")
    public ResponseEntity updateUserLocation(@RequestBody UpdateLocationRequest updateLocationRequest ){
        try {
            if ((SecurityContextHolder.getContext().getAuthentication()) != null) {
                AppUser userDetails = userRepository.findByUserName((SecurityContextHolder.getContext().getAuthentication()).getName());
                userDetails.setLocation(updateLocationRequest.getLocation());
                userRepository.save(userDetails);
                return new ResponseEntity(HttpStatus.OK);
            }
        } catch (Exception ex) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }


}