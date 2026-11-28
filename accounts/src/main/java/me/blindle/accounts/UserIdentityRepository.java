package me.blindle.accounts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UserIdentity.Key> {

    List<UserIdentity> findByEmailAndEmailVerifiedTrue(String email);

    List<UserIdentity> findByAccountId(java.util.UUID accountId);
}
