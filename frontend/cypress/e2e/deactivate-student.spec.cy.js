// UC-STU-deactivate-student steps 4 and 4a. The deactivation request is stubbed. Login and the student
// list use the dev stack. Run with the backend and `npm run dev` up.

const deactivated = {
  flag: true,
  code: 200,
  message: 'This student has been deactivated.',
  data: {
    id: 4,
    username: 'j.smith@abc.edu',
    enabled: false,
    firstName: 'John',
    lastName: 'Smith',
    email: 'j.smith@abc.edu'
  }
}

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/*/deactivation', { body: deactivated }).as('deactivate')
    cy.visit('/students')
    cy.get('[data-cy=student-search-first-name] input').type('John')
    cy.get('[data-cy=edit-student]').first().click()
    cy.get('[data-cy=student-status]').should('contain', 'Enabled')
  })

  it('dismissing the warning sends no request and leaves her enabled (4a)', () => {
    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.cy-deactivate-cancel').click()

    cy.get('[data-cy=student-status]').should('contain', 'Enabled')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('the warning names the consequences and Confirm deactivates her once (step 4)', () => {
    cy.get('[data-cy=deactivate-student]').click()

    cy.get('.el-message-box')
      .should('contain', 'can no longer log in')
      .and('contain', 'stays on her team')
      .and('contain', 'no longer owes')
      .and('contain', 'already stored are kept')
      .and('contain', 'peer evaluation report')
      .and('contain', 'does not delete')
    cy.get('.cy-deactivate-confirm').click()

    cy.wait('@deactivate')
    cy.get('@deactivate.all').should('have.length', 1)
    cy.get('[data-cy=student-status]').should('contain', 'Disabled')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
  })
})
