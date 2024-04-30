package com.insider.login.member.service;

import com.insider.login.member.entity.Member;
import com.insider.login.member.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MemberService {

    private MemberRepository memberRepository;

    public MemberService(MemberRepository userRepository) {
        this.memberRepository = userRepository;
    }

    public Optional<Member> findMember(int id) {
//        System.out.println("최종적으로 받아온 id값은: " + id);
//        System.out.println("id의 타입: " + id.);

        Optional<Member> member = memberRepository.findByMemberId(id);
        System.out.println("찾은 member는: " + member);

        /*
        * 별도의 검증 로직 작성
        * 예) DB 문제로 구성원의 정보를 받지 못했다거나
        *    또는 여러 구성원들의 정보들을 반환을 했다거나...
        * 추가적인 로직을 작성을 해야한다.... no...
        * */

//        return null;
//        return userRepository.findById(id);
        return member;
    }

    public Member saveMember(Member member) {
        memberRepository.save(member);
        return member;
    }
}
