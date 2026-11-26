package net.bestia.api.bugreport

import org.springframework.data.jpa.repository.JpaRepository

interface BugCrashSignatureRepository : JpaRepository<BugCrashSignature, String>
