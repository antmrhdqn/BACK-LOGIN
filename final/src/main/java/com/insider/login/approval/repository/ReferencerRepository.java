package com.insider.login.approval.repository;

import com.insider.login.approval.entity.Referencer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReferencerRepository {

    @PersistenceContext
    private EntityManager manager;

    public void save (Referencer referencer){
        manager.persist(referencer);
    }

    public List<Referencer> findByApprovalId(String approvalNo) {
        List<Referencer> referencerList = manager.createQuery("SELECT r FROM Referencer r WHERE r.approvalNo= :approvalNo", Referencer.class)
                .setParameter("approvalNo", approvalNo)
                .getResultList();

        return referencerList;
    }
}
