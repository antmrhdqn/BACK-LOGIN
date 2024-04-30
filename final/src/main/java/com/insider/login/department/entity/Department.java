package com.insider.login.department.entity;

import com.insider.login.member.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;


import java.util.List;

@Entity
@Table(name = "department_info")
@AllArgsConstructor
@Getter
@ToString
public class Department {

    @Id
    @Column(name = "depart_no", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int departNo;                   // 부서 번호

    @Column(name = "depart_name", nullable = false)
    private String departName;              // 부서명
    @OneToMany(mappedBy = "department")
    private List<Member> members;           // 구성원 리스트

    protected Department() {
    }

    public Department(int departNo, String departName) {
        this.departNo = departNo;
        this.departName = departName;
    }
}